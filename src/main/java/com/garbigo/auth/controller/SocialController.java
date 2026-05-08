package com.garbigo.auth.controller;

import com.garbigo.auth.dto.*;
import com.garbigo.auth.service.SocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    // ====================== FOLLOW ======================
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

    @GetMapping("/followers/{userId}")
    public ResponseEntity<List<UserSummaryDto>> getFollowers(@PathVariable String userId) {
        return ResponseEntity.ok(socialService.getFollowers(userId));
    }

    @GetMapping("/following/{userId}")
    public ResponseEntity<List<UserSummaryDto>> getFollowing(@PathVariable String userId) {
        return ResponseEntity.ok(socialService.getFollowing(userId));
    }

    @GetMapping("/is-following/{userId}")
    public ResponseEntity<FollowCheckDto> isFollowing(@PathVariable String userId) {
        return ResponseEntity.ok(socialService.isFollowing(userId));
    }

    // ====================== LIKE ======================
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

    @GetMapping("/is-liked")
    public ResponseEntity<LikeCheckDto> isLiked(@RequestParam String targetId,
                                                @RequestParam(required = false) String targetType) {
        return ResponseEntity.ok(socialService.isLiked(targetId, targetType));
    }

    // ====================== REVIEW ======================
    @PostMapping("/review")
    public ResponseEntity<String> addReview(@RequestBody SocialActionRequest request) {
        socialService.addReview(request.getTargetId(), request.getTargetType(), request);
        return ResponseEntity.ok("Review submitted successfully");
    }

    @PutMapping("/review/{reviewId}")
    public ResponseEntity<String> updateReview(@PathVariable String reviewId,
                                               @RequestBody ReviewUpdateRequest request) {
        socialService.updateReview(reviewId, request);
        return ResponseEntity.ok("Review updated successfully");
    }

    @DeleteMapping("/review/{reviewId}")
    public ResponseEntity<String> deleteReview(@PathVariable String reviewId) {
        socialService.deleteReview(reviewId);
        return ResponseEntity.ok("Review deleted successfully");
    }

    @GetMapping("/reviews/{targetId}")
    public ResponseEntity<List<ReviewResponseDto>> getReviews(
            @PathVariable String targetId,
            @RequestParam(required = false) String targetType) {
        return ResponseEntity.ok(socialService.getReviews(targetId, targetType));
    }

    // ====================== STATS ======================
    @GetMapping("/stats/{userId}")
    public ResponseEntity<SocialStatsDto> getStats(@PathVariable String userId) {
        return ResponseEntity.ok(socialService.getUserStats(userId));
    }
}