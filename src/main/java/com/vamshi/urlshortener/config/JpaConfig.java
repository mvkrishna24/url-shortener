package com.vamshi.urlshortener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA and persistence configuration.
 *
 * HikariCP pool settings (max-pool-size=20, connection-timeout=5s) live in
 * application.yml under spring.datasource.hikari — Spring Boot auto-configures
 * the HikariDataSource from those properties. No @Bean override is needed here.
 *
 * @EnableJpaAuditing activates @CreatedDate / @LastModifiedDate on entities.
 * This project currently uses Hibernate's @CreationTimestamp / @UpdateTimestamp
 * which work independently of this annotation, but enabling JPA auditing now
 * unlocks @LastModifiedBy / @CreatedBy (populated via AuditorAware) — useful for
 * audit trails ("who last updated this URL?"). AuditorAware bean added in auth prompt.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
