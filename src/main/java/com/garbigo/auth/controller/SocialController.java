package com.garbigo.auth.controller;

import com.garbigo.auth.dto.SocialActionRequest;
import com.garbigo.auth.dto.SocialStatsDto;
import com.garbigo.auth.service.SocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @PostMapping("/follow/{userId}")
    public ResponseEntity<String> follow(@PathVariable String userId) {
        socialService.follow(userId);
        return ResponseEntity.ok("Followed successfully");
    }

    @DeleteMapping("/follow/{userId}")
    public ResponseEntity<String> unfollow(@PathVariable String userId) {
        socialService.unfollow(userId);
        return ResponseEntity.ok("Unfollowed successfully");
    }

    @PostMapping("/like")
    public ResponseEntity<String> like(@RequestBody SocialActionRequest request) {
        socialService.like(request.getTargetId(), request.getTargetType());
        return ResponseEntity.ok("Liked successfully");
    }

    @DeleteMapping("/like")
    public ResponseEntity<String> unlike(@RequestBody SocialActionRequest request) {
        socialService.unlike(request.getTargetId(), request.getTargetType());
        return ResponseEntity.ok("Unliked successfully");
    }

    @PostMapping("/review")
    public ResponseEntity<String> addReview(@RequestBody SocialActionRequest request) {
        socialService.addReview(request.getTargetId(), request.getTargetType(), request);
        return ResponseEntity.ok("Review submitted successfully");
    }

    @GetMapping("/stats/{userId}")
    public ResponseEntity<SocialStatsDto> getStats(@PathVariable String userId) {
        return ResponseEntity.ok(socialService.getUserStats(userId));
    }
}