package com.vamshi.urlshortener.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdGeneratorService {

    private final JdbcTemplate jdbcTemplate;

    public IdGeneratorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long generateId() {
        return jdbcTemplate.queryForObject("SELECT nextval('urls_id_seq')", Long.class);
    }
}