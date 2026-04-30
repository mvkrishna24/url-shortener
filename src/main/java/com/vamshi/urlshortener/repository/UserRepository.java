package com.vamshi.urlshortener.repository;

import com.vamshi.urlshortener.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Used by Spring Security's UserDetailsService to load a user by email at login.
    Optional<User> findByEmail(String email);

    // Existence check before registration — avoids a full SELECT + object hydration.
    boolean existsByEmail(String email);
}
