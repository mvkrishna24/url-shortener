package com.vamshi.urlshortener.user;

import com.vamshi.urlshortener.entity.Url;
import com.vamshi.urlshortener.repository.UrlRepository;
import com.vamshi.urlshortener.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final UrlRepository urlRepository; // Ensure your UrlRepository handles pagination properly

    @GetMapping("/auth/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal Long userId) {
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(new UserResponse(user.getId(), user.getEmail())))
                .orElse(ResponseEntity.notFound().build());
    }

    // Example demonstrating method-level security ensuring the context has a principal
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/users/me/urls")
    public ResponseEntity<Page<Url>> getUserUrls(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        int safeSize = Math.min(size, 100);
        return ResponseEntity.ok(urlRepository.findByUser_Id(userId, PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
}