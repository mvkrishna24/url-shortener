package com.vamshi.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Explicit Redis bean configuration.
 *
 * Spring Boot auto-configures StringRedisTemplate via RedisAutoConfiguration, but
 * declaring it here makes the serialization contract visible in the codebase:
 * both keys and values are plain UTF-8 strings — no Java serialization, no JSON
 * overhead. This matters because the cache stores only short codes (keys) and URLs
 * (values), which are already strings.
 *
 * Connection pool (max-active=16, min-idle=2) is configured in application.yml under
 * spring.data.redis.lettuce.pool. Lettuce uses non-blocking I/O, so a pool of 16
 * multiplexed connections is sufficient for thousands of concurrent redirects.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
