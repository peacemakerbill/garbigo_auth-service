package com.garbigo.auth.service;

import com.garbigo.auth.dto.SocialActionRequest;
import com.garbigo.auth.dto.SocialStatsDto;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.Follow;
import com.garbigo.auth.model.Like;
import com.garbigo.auth.model.Review;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.FollowRepository;
import com.garbigo.auth.repository.LikeRepository;
import com.garbigo.auth.repository.ReviewRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;


@Service
public class SocialService {

    private final FollowRepository followRepository;
    private final LikeRepository likeRepository;
    private final ReviewRepository reviewRepository;

    public SocialService(FollowRepository followRepository,
                         LikeRepository likeRepository,
                         ReviewRepository reviewRepository) {
        this.followRepository = followRepository;
        this.likeRepository = likeRepository;
        this.reviewRepository = reviewRepository;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException("No authenticated user");
        }
        return (User) authentication.getPrincipal();
    }

    // ====================== FOLLOW ======================
    public void follow(String targetUserId) {
        User currentUser = getCurrentUser();
        if (currentUser.getId().equals(targetUserId)) {
            throw new CustomException("You cannot follow yourself");
        }

        Follow follow = new Follow();
        follow.setUserId(targetUserId);
        follow.setFollowerId(currentUser.getId());
        followRepository.save(follow);
    }

    public void unfollow(String targetUserId) {
        User currentUser = getCurrentUser();
        followRepository.deleteByUserIdAndFollowerId(targetUserId, currentUser.getId());
    }

    // ====================== LIKE ======================
    public void like(String targetId, String targetType) {
        User currentUser = getCurrentUser();

        Like like = new Like();
        like.setUserId(currentUser.getId());
        like.setTargetId(targetId);
        like.setTargetType(targetType != null ? targetType : "USER");
        likeRepository.save(like);
    }

    public void unlike(String targetId, String targetType) {
        User currentUser = getCurrentUser();
        likeRepository.deleteByUserIdAndTargetIdAndTargetType(
                currentUser.getId(), targetId, targetType != null ? targetType : "USER");
    }

    // ====================== REVIEW ======================
    public void addReview(String targetId, String targetType, SocialActionRequest request) {
        User currentUser = getCurrentUser();

        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new CustomException("Rating must be between 1 and 5");
        }

        Review review = new Review();
        review.setUserId(currentUser.getId());
        review.setTargetId(targetId);
        review.setTargetType(targetType != null ? targetType : "USER");
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        reviewRepository.save(review);
    }

    // ====================== STATS ======================
    public SocialStatsDto getUserStats(String userId) {
        long followers = followRepository.countByUserId(userId);
        long following = followRepository.countByFollowerId(userId);
        long likes = likeRepository.countByTargetIdAndTargetType(userId, "USER");
        Double avgRating = reviewRepository.getAverageRatingByTargetIdAndTargetType(userId, "USER");

        return new SocialStatsDto(followers, following, likes, avgRating != null ? avgRating : 0.0);
    }
}