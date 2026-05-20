package com.garbigo.auth.service;

import com.cloudinary.Cloudinary;
import com.garbigo.auth.dto.ProfileUpdateDto;
import com.garbigo.auth.dto.ProfileUpdateRequest;
import com.garbigo.auth.dto.UserDto;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.Role;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.UserRepository;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper = new ModelMapper();
    private final AuthService authService;

    public UserService(UserRepository userRepository, Cloudinary cloudinary,
                       PasswordEncoder passwordEncoder, AuthService authService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    public UserDto getCurrentUserDto(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found"));
        
        return modelMapper.map(user, UserDto.class);
    }

    // ====================== USER PROFILE ======================

    /**
     * Update user profile using JSON (text fields only)
     */
    public UserDto updateProfile(ProfileUpdateDto dto) {
        User user = getCurrentUser();
        updateUserFields(user, dto);
        userRepository.save(user);
        return modelMapper.map(user, UserDto.class);
    }

    /**
     * Update profile with multipart request (mainly for profile picture)
     */
    public UserDto updateProfile(ProfileUpdateRequest request) {
        User user = getCurrentUser();

        // Update text fields if provided
        updateUserFields(user, request);

        // Handle profile picture upload
        if (request.getProfilePicture() != null && !request.getProfilePicture().isEmpty()) {
            try {
                String contentType = request.getProfilePicture().getContentType();
                if (contentType != null && !contentType.startsWith("image/")) {
                    throw new CustomException("Only image files are allowed for profile picture");
                }

                String url = authService.uploadProfilePicture(request.getProfilePicture());
                user.setProfilePictureUrl(url);
            } catch (Exception e) {
                throw new CustomException("Failed to upload profile picture: " + e.getMessage());
            }
        }

        userRepository.save(user);
        return modelMapper.map(user, UserDto.class);
    }

    // ====================== HELPER METHODS ======================

    private void updateUserFields(User user, Object request) {
        if (request instanceof ProfileUpdateDto dto) {
            updateFromDto(user, dto);
        } else if (request instanceof ProfileUpdateRequest req) {
            updateFromRequest(user, req);
        }
    }

    private void updateFromDto(User user, ProfileUpdateDto dto) {
        if (dto.getEmail() != null && !dto.getEmail().trim().isEmpty()
                && !dto.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
                throw new CustomException("Email already in use by another user");
            }
            user.setEmail(dto.getEmail());
        }

        if (dto.getPhoneNumber() != null && !dto.getPhoneNumber().trim().isEmpty()
                && !dto.getPhoneNumber().equals(user.getPhoneNumber())) {
            if (userRepository.findByPhoneNumber(dto.getPhoneNumber()).isPresent()) {
                throw new CustomException("Phone number already in use by another user");
            }
            user.setPhoneNumber(dto.getPhoneNumber());
        }

        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getMiddleName() != null) user.setMiddleName(dto.getMiddleName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());
        if (dto.getHomeAddress() != null) user.setHomeAddress(dto.getHomeAddress());
        if (dto.getWastePreferences() != null) user.setWastePreferences(dto.getWastePreferences());
        if (dto.getCollectionSchedule() != null) user.setCollectionSchedule(dto.getCollectionSchedule());
    }

    private void updateFromRequest(User user, ProfileUpdateRequest req) {
        if (req.getEmail() != null && !req.getEmail().trim().isEmpty()
                && !req.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(req.getEmail()).isPresent()) {
                throw new CustomException("Email already in use by another user");
            }
            user.setEmail(req.getEmail());
        }

        if (req.getPhoneNumber() != null && !req.getPhoneNumber().trim().isEmpty()
                && !req.getPhoneNumber().equals(user.getPhoneNumber())) {
            if (userRepository.findByPhoneNumber(req.getPhoneNumber()).isPresent()) {
                throw new CustomException("Phone number already in use by another user");
            }
            user.setPhoneNumber(req.getPhoneNumber());
        }

        if (req.getFirstName() != null) user.setFirstName(req.getFirstName());
        if (req.getMiddleName() != null) user.setMiddleName(req.getMiddleName());
        if (req.getLastName() != null) user.setLastName(req.getLastName());
        if (req.getHomeAddress() != null) user.setHomeAddress(req.getHomeAddress());
        if (req.getWastePreferences() != null) user.setWastePreferences(req.getWastePreferences());
        if (req.getCollectionSchedule() != null) user.setCollectionSchedule(req.getCollectionSchedule());
    }

    // ====================== ADMIN OPERATIONS ======================

    public UserDto createUser(User user) {
        if (user.getEmail() != null && userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new CustomException("Email already in use");
        }
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().trim().isEmpty()) {
            if (userRepository.findByPhoneNumber(user.getPhoneNumber()).isPresent()) {
                throw new CustomException("Phone number already in use");
            }
        }

        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        userRepository.save(user);
        return modelMapper.map(user, UserDto.class);
    }

    public UserDto updateUser(String id, User update) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException("User not found"));

        if (update.getEmail() != null && !update.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(update.getEmail()).isPresent()) {
                throw new CustomException("Email already in use by another user");
            }
            user.setEmail(update.getEmail());
        }

        if (update.getPhoneNumber() != null && !update.getPhoneNumber().equals(user.getPhoneNumber())) {
            if (userRepository.findByPhoneNumber(update.getPhoneNumber()).isPresent()) {
                throw new CustomException("Phone number already in use by another user");
            }
            user.setPhoneNumber(update.getPhoneNumber());
        }

        if (update.getFirstName() != null) user.setFirstName(update.getFirstName());
        if (update.getMiddleName() != null) user.setMiddleName(update.getMiddleName());
        if (update.getLastName() != null) user.setLastName(update.getLastName());
        if (update.getHomeAddress() != null) user.setHomeAddress(update.getHomeAddress());
        if (update.getRole() != null) user.setRole(update.getRole());

        userRepository.save(user);
        return modelMapper.map(user, UserDto.class);
    }

    public void deleteUser(String id) {
        User userToDelete = userRepository.findById(id)
                .orElseThrow(() -> new CustomException("User not found"));

        User currentUser = getCurrentUser();

        if (userToDelete.getId().equals(currentUser.getId())) {
            throw new CustomException("You cannot delete your own account");
        }

        if (userToDelete.getRole() == Role.ADMIN) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new CustomException("Cannot delete the last admin account");
            }
        }

        userRepository.deleteById(id);
    }

    public void archiveUser(String id) {
        preventSelfModification(id, "archive");
        preventLastAdminModification(id, "archive");

        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException("User not found"));

        if (user.isArchived()) {
            throw new CustomException("User is already archived");
        }

        user.setArchived(true);
        userRepository.save(user);
    }

    public void unarchiveUser(String id) {
        preventSelfModification(id, "unarchive");

        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException("User not found"));

        if (!user.isArchived()) {
            throw new CustomException("User is not archived");
        }

        user.setArchived(false);
        userRepository.save(user);
    }

    public void activateUser(String id) {
        preventSelfModification(id, "activate");

        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException("User not found"));

        if (user.isActive()) {
            throw new CustomException("User is already active");
        }

        user.setActive(true);
        userRepository.save(user);
    }

    public void deactivateUser(String id) {
        preventSelfModification(id, "deactivate");
        preventLastAdminModification(id, "deactivate");

        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException("User not found"));

        if (!user.isActive()) {
            throw new CustomException("User is already deactivated");
        }

        user.setActive(false);
        userRepository.save(user);
    }

    public void verifyUser(String id) {
        preventSelfModification(id, "verify");

        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException("User not found"));

        if (user.isVerified()) {
            throw new CustomException("User is already verified");
        }

        user.setVerified(true);
        userRepository.save(user);
    }

    public void unverifyUser(String id) {
        preventSelfModification(id, "unverify");
        preventLastAdminModification(id, "unverify");

        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException("User not found"));

        if (!user.isVerified()) {
            throw new CustomException("User is not verified");
        }

        user.setVerified(false);
        userRepository.save(user);
    }

    // ====================== HELPER METHODS ======================

    private void preventSelfModification(String targetId, String action) {
        User current = getCurrentUser();
        if (current.getId().equals(targetId)) {
            throw new CustomException("You cannot " + action + " your own account");
        }
    }

    private void preventLastAdminModification(String targetId, String action) {
        User targetUser = userRepository.findById(targetId)
                .orElseThrow(() -> new CustomException("User not found"));

        if (targetUser.getRole() == Role.ADMIN) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new CustomException("Cannot " + action + " the last admin account");
            }
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException("No authenticated user");
        }
        return (User) authentication.getPrincipal();
    }

    public List<UserDto> getAllUsers(String search) {
        List<User> users = search == null || search.isBlank()
                ? userRepository.findAll()
                : userRepository.searchUsers(search);

        return users.stream()
                .map(u -> modelMapper.map(u, UserDto.class))
                .collect(Collectors.toList());
    }
}