package com.garbigo.auth.controller;

import com.garbigo.auth.dto.ProfileUpdateRequest;
import com.garbigo.auth.dto.UserDto;
import com.garbigo.auth.model.LiveLocation;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.LiveLocationRepository;
import com.garbigo.auth.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @Autowired
    private LiveLocationRepository liveLocationRepository;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Update current user profile (existing)
    @PutMapping("/profile")
    public ResponseEntity<UserDto> updateProfile(@RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    // Live location update from mobile app (Collector)
    @PostMapping("/live-location")
    public ResponseEntity<?> updateLiveLocation(
            @AuthenticationPrincipal User authenticatedUser,
            @RequestBody Map<String, Object> locationData) {

        try {
            // Validate and parse required fields
            Object latObj = locationData.get("latitude");
            Object lngObj = locationData.get("longitude");
            if (latObj == null || lngObj == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "latitude and longitude are required"));
            }

            double latitude = Double.parseDouble(latObj.toString());
            double longitude = Double.parseDouble(lngObj.toString());

            // Optional timestamp, default to now
            long timestampMillis = System.currentTimeMillis();
            if (locationData.containsKey("timestamp")) {
                timestampMillis = Long.parseLong(locationData.get("timestamp").toString());
            }
            Instant timestamp = Instant.ofEpochMilli(timestampMillis);

            // Save live location
            LiveLocation liveLocation = new LiveLocation();
            liveLocation.setUserId(authenticatedUser.getId());
            liveLocation.setLatitude(latitude);
            liveLocation.setLongitude(longitude);
            liveLocation.setTimestamp(timestamp);

            liveLocationRepository.save(liveLocation);

            return ResponseEntity.ok(Map.of(
                    "message", "Location updated successfully",
                    "userId", authenticatedUser.getId(),
                    "latitude", latitude,
                    "longitude", longitude
            ));

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid latitude or longitude format"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to save location"));
        }
    }

    // Admin: Get all users
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(userService.getAllUsers(search));
    }

    // Admin: Create user
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody User user) {
        return ResponseEntity.ok(userService.createUser(user));
    }

    // Admin: Update user
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable String id, @RequestBody User user) {
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    // Admin: Delete user
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted");
    }

    // Admin: Archive/Unarchive
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

    // Admin: Activate/Deactivate
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

    // Admin: Verify/Unverify
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