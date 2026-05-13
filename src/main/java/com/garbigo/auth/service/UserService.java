package com.garbigo.auth.service;

import com.cloudinary.Cloudinary;
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
    private final Cloudinary cloudinary;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper = new ModelMapper();
    private final AuthService authService;

    public UserService(UserRepository userRepository, Cloudinary cloudinary, 
                       PasswordEncoder passwordEncoder, AuthService authService) {
        this.userRepository = userRepository;
        this.cloudinary = cloudinary;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    // ====================== USER PROFILE ======================
    public UserDto updateProfile(ProfileUpdateRequest request) {
        User user = getCurrentUser();

        // Email update
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty() 
                && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new CustomException("Email already in use by another user");
            }
            user.setEmail(request.getEmail());
        }

        // Phone number update
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty() 
                && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
            if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
                throw new CustomException("Phone number already in use by another user");
            }
            user.setPhoneNumber(request.getPhoneNumber());
        }

        // Other fields
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getMiddleName() != null) user.setMiddleName(request.getMiddleName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getHomeAddress() != null) user.setHomeAddress(request.getHomeAddress());
        if (request.getWastePreferences() != null) user.setWastePreferences(request.getWastePreferences());
        if (request.getCollectionSchedule() != null) user.setCollectionSchedule(request.getCollectionSchedule());

        // Profile picture
        if (request.getProfilePicture() != null && !request.getProfilePicture().isEmpty()) {
            try {
                String url = authService.uploadProfilePicture(request.getProfilePicture());
                user.setProfilePictureUrl(url);
            } catch (Exception e) {
                throw new CustomException("Failed to upload profile picture");
            }
        }

        userRepository.save(user);
        return modelMapper.map(user, UserDto.class);
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