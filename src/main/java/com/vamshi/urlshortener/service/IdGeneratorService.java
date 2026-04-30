package com.vamshi.urlshortener.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Batch-allocated ID generator. Fetches blocks of {@value BLOCK_SIZE} IDs from a PostgreSQL
 * sequence in one DB round-trip, then serves them lock-free via CAS until the block is
 * exhausted. At 10K req/s this reduces sequence calls by 1000×.
 *
 * Thread-safety: CAS fast-path is lock-free. Block refresh uses a dedicated {@code blockLock}
 * (not {@code this}) so Spring AOP proxies cannot inadvertently hold the lock.
 */
@Service
public class IdGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(IdGeneratorService.class);

    static final int BLOCK_SIZE = 1000;
    private static final String NEXT_VAL_SQL = "SELECT nextval('url_id_seq')";

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong currentId = new AtomicLong(Long.MAX_VALUE);
    private volatile long blockEnd = Long.MAX_VALUE;
    private final Object blockLock = new Object();

    public IdGeneratorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void init() {
        fetchNewBlock();
        log.info("IdGeneratorService initialized; first block starts at {}", currentId.get());
    }

    public long nextId() {
        while (true) {
            long id = currentId.get();
            if (id < blockEnd) {
                if (currentId.compareAndSet(id, id + 1)) {
                    return id;
                }
            } else {
                ensureNextBlock();
            }
        }
    }

    private void ensureNextBlock() {
        synchronized (blockLock) {
            if (currentId.get() < blockEnd) {
                return; // another thread already refreshed
            }
            fetchNewBlock();
        }
    }

    private void fetchNewBlock() {
        Long blockStart = jdbcTemplate.queryForObject(NEXT_VAL_SQL, Long.class);
        if (blockStart == null) {
            throw new IllegalStateException("url_id_seq returned null — check DB connectivity");
        }
        // Write blockEnd before currentId so no thread observes currentId >= old blockEnd
        // during the two-write window.
        blockEnd = blockStart + BLOCK_SIZE;
        currentId.set(blockStart);
        log.debug("Allocated ID block [{}, {})", blockStart, blockEnd);
    }
}