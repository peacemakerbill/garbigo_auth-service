package com.garbigo.auth.service;

import com.garbigo.auth.dto.*;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.Follow;
import com.garbigo.auth.model.Like;
import com.garbigo.auth.model.Review;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.FollowRepository;
import com.garbigo.auth.repository.LikeRepository;
import com.garbigo.auth.repository.ReviewRepository;
import com.garbigo.auth.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SocialService {

    private final FollowRepository followRepository;
    private final LikeRepository likeRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public SocialService(FollowRepository followRepository,
                         LikeRepository likeRepository,
                         ReviewRepository reviewRepository,
                         UserRepository userRepository) {
        this.followRepository = followRepository;
        this.likeRepository = likeRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
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
        User current = getCurrentUser();
        if (current.getId().equals(targetUserId)) {
            throw new CustomException("You cannot follow yourself");
        }
        Follow follow = new Follow();
        follow.setUserId(targetUserId);
        follow.setFollowerId(current.getId());
        followRepository.save(follow);
    }

    public void unfollow(String targetUserId) {
        User current = getCurrentUser();
        followRepository.deleteByUserIdAndFollowerId(targetUserId, current.getId());
    }

    public List<UserSummaryDto> getFollowers(String userId) {
        List<String> followerIds = followRepository.findByUserId(userId)
                .stream().map(Follow::getFollowerId).collect(Collectors.toList());
        return getUserSummaries(followerIds);
    }

    public List<UserSummaryDto> getFollowing(String userId) {
        List<String> followingIds = followRepository.findByFollowerId(userId)
                .stream().map(Follow::getUserId).collect(Collectors.toList());
        return getUserSummaries(followingIds);
    }

    public FollowCheckDto isFollowing(String targetUserId) {
        User current = getCurrentUser();
        boolean following = followRepository.findByUserIdAndFollowerId(targetUserId, current.getId()).isPresent();
        return new FollowCheckDto(following);
    }

    // ====================== LIKE ======================
    public void like(String targetId, String targetType) {
        User current = getCurrentUser();
        Like like = new Like();
        like.setUserId(current.getId());
        like.setTargetId(targetId);
        like.setTargetType(targetType != null ? targetType.toUpperCase() : "USER");
        likeRepository.save(like);
    }

    public void unlike(String targetId, String targetType) {
        User current = getCurrentUser();
        likeRepository.deleteByUserIdAndTargetIdAndTargetType(
                current.getId(), targetId, targetType != null ? targetType.toUpperCase() : "USER");
    }

    public LikeCheckDto isLiked(String targetId, String targetType) {
        User current = getCurrentUser();
        boolean liked = likeRepository.findByUserIdAndTargetIdAndTargetType(
                current.getId(), targetId, targetType != null ? targetType.toUpperCase() : "USER").isPresent();
        return new LikeCheckDto(liked);
    }

    // ====================== REVIEW ======================
    public void addReview(String targetId, String targetType, SocialActionRequest request) {
        User current = getCurrentUser();

        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new CustomException("Rating must be between 1 and 5");
        }

        Review review = new Review();
        review.setUserId(current.getId());
        review.setTargetId(targetId);
        review.setTargetType(targetType != null ? targetType.toUpperCase() : "USER");
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        reviewRepository.save(review);
    }

    public void updateReview(String reviewId, ReviewUpdateRequest request) {
        User current = getCurrentUser();
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException("Review not found"));

        if (!review.getUserId().equals(current.getId())) {
            throw new CustomException("You can only edit your own review");
        }

        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new CustomException("Rating must be between 1 and 5");
        }

        review.setRating(request.getRating());
        review.setComment(request.getComment());
        reviewRepository.save(review);
    }

    public void deleteReview(String reviewId) {
        User current = getCurrentUser();
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException("Review not found"));

        if (!review.getUserId().equals(current.getId())) {
            throw new CustomException("You can only delete your own review");
        }

        reviewRepository.deleteById(reviewId);
    }

    public List<ReviewResponseDto> getReviews(String targetId, String targetType) {
        List<Review> reviews = reviewRepository.findByTargetIdAndTargetType(
                targetId, targetType != null ? targetType.toUpperCase() : "USER");

        if (reviews.isEmpty()) {
            return List.of();
        }

        List<String> reviewerIds = reviews.stream()
                .map(Review::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, User> userMap = userRepository.findAllById(reviewerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return reviews.stream()
                .map(r -> {
                    User reviewer = userMap.get(r.getUserId());
                    String fullName = reviewer != null 
                            ? reviewer.getFirstName() + " " + (reviewer.getLastName() != null ? reviewer.getLastName() : "")
                            : "Unknown User";

                    return new ReviewResponseDto(
                            r.getId(),
                            r.getUserId(),
                            fullName.trim(),
                            reviewer != null ? reviewer.getProfilePictureUrl() : null,
                            r.getRating(),
                            r.getComment(),
                            r.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());
    }

    // ====================== HELPER ======================
    private List<UserSummaryDto> getUserSummaries(List<String> userIds) {
        if (userIds.isEmpty()) return List.of();

        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return userIds.stream()
                .map(id -> {
                    User u = userMap.get(id);
                    if (u == null) {
                        return new UserSummaryDto(id, null, null, null, null);
                    }
                    return new UserSummaryDto(
                            u.getId(),
                            u.getUsername(),
                            u.getFirstName(),
                            u.getLastName(),
                            u.getProfilePictureUrl()
                    );
                })
                .collect(Collectors.toList());
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