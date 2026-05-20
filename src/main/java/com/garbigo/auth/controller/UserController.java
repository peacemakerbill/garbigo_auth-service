package com.garbigo.auth.controller;

import com.garbigo.auth.dto.LiveLocationResponseDto;
import com.garbigo.auth.dto.ProfileUpdateRequest;
import com.garbigo.auth.dto.UserDto;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.LiveLocation;
import com.garbigo.auth.model.Role;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.LiveLocationRepository;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.service.LiveLocationRedisService;
import com.garbigo.auth.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final LiveLocationRedisService liveLocationRedisService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LiveLocationRepository liveLocationRepository;

    public UserController(UserService userService, 
                         LiveLocationRedisService liveLocationRedisService) {
        this.userService = userService;
        this.liveLocationRedisService = liveLocationRedisService;
    }
    
    // ====================== CURRENT USER PROFILE ======================
    @GetMapping("/profile")
    public ResponseEntity<UserDto> getCurrentProfile(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        UserDto userDto = userService.getCurrentUserDto(currentUser.getId());
        return ResponseEntity.ok(userDto);
    }

    // Update current user profile - Supports text fields + image upload
    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDto> updateProfile(
            @ModelAttribute ProfileUpdateRequest request) {
        
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    // ====================== LIVE LOCATION ======================
    
    @PostMapping("/live-location")
    public ResponseEntity<?> updateLiveLocation(
            @AuthenticationPrincipal User authenticatedUser,
            @RequestBody Map<String, Object> locationData) {

        try {
            Object latObj = locationData.get("latitude");
            Object lngObj = locationData.get("longitude");

            if (latObj == null || lngObj == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "latitude and longitude are required"));
            }

            double latitude = Double.parseDouble(latObj.toString());
            double longitude = Double.parseDouble(lngObj.toString());

            liveLocationRedisService.updateLiveLocation(
                    authenticatedUser.getId(), latitude, longitude);

            LiveLocation liveLocation = new LiveLocation();
            liveLocation.setUserId(authenticatedUser.getId());
            liveLocation.setLatitude(latitude);
            liveLocation.setLongitude(longitude);

            liveLocationRepository.save(liveLocation);

            return ResponseEntity.ok(Map.of(
                    "message", "Live location updated successfully",
                    "userId", authenticatedUser.getId(),
                    "latitude", latitude,
                    "longitude", longitude
            ));

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid latitude or longitude format"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to update location: " + e.getMessage()));
        }
    }

    @GetMapping("/live-location/{userId}")
    public ResponseEntity<?> getCurrentLiveLocation(@PathVariable String userId,
                                                    @AuthenticationPrincipal User currentUser) {

        if (currentUser == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "You must be logged in to view live locations"));
        }

        LiveLocation location = liveLocationRedisService.getCurrentLocation(userId);
        
        if (location == null) {
            return ResponseEntity.ok(Map.of(
                "message", "No active live location found",
                "userId", userId,
                "active", false
            ));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found"));

        String fullName = buildFullName(user);

        LiveLocationResponseDto response = new LiveLocationResponseDto(
                user.getId(),
                user.getFirstName(),
                user.getMiddleName(),
                user.getLastName(),
                fullName,
                user.getEmail(),
                user.getPhoneNumber(),
                user.getProfilePictureUrl(),
                user.getRole(),
                location.getLatitude(),
                location.getLongitude(),
                location.getTimestamp() != null ? location.getTimestamp() : Instant.now(),
                true
        );

        return ResponseEntity.ok(response);
    }

    // ====================== CLIENT: SEARCH COLLECTORS ======================
    @GetMapping("/collectors")
    public ResponseEntity<List<UserDto>> getCollectors(
            @RequestParam(required = false) String search) {
        
        List<UserDto> allUsers = userService.getAllUsers(search);
        
        // Filter only collectors
        List<UserDto> collectors = allUsers.stream()
                .filter(user -> user.getRole() == Role.COLLECTOR)
                .collect(Collectors.toList());

        return ResponseEntity.ok(collectors);
    }

    // Helper method
    private String buildFullName(User user) {
        StringBuilder sb = new StringBuilder();
        if (user.getFirstName() != null) sb.append(user.getFirstName().trim());
        if (user.getMiddleName() != null) sb.append(" ").append(user.getMiddleName().trim());
        if (user.getLastName() != null) sb.append(" ").append(user.getLastName().trim());
        return sb.toString().trim();
    }

    // ====================== ADMIN ENDPOINTS ======================

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(userService.getAllUsers(search));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody User user) {
        return ResponseEntity.ok(userService.createUser(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable String id, @RequestBody User user) {
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/archive")
    public ResponseEntity<String> archiveUser(@PathVariable String id) {
        userService.archiveUser(id);
        return ResponseEntity.ok("User archived");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/unarchive")
    public ResponseEntity<String> unarchiveUser(@PathVariable String id) {
        userService.unarchiveUser(id);
        return ResponseEntity.ok("User unarchived");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/activate")
    public ResponseEntity<String> activateUser(@PathVariable String id) {
        userService.activateUser(id);
        return ResponseEntity.ok("User activated");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<String> deactivateUser(@PathVariable String id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok("User deactivated");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/verify")
    public ResponseEntity<String> verifyUser(@PathVariable String id) {
        userService.verifyUser(id);
        return ResponseEntity.ok("User verified");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/unverify")
    public ResponseEntity<String> unverifyUser(@PathVariable String id) {
        userService.unverifyUser(id);
        return ResponseEntity.ok("User unverified");
    }
}