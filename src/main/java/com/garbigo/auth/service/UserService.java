package com.garbigo.auth.service;

import com.cloudinary.Cloudinary;
import com.garbigo.auth.dto.ProfileUpdateRequest;
import com.garbigo.auth.dto.UserDto;
import com.garbigo.auth.exception.CustomException;
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

    public UserService(UserRepository userRepository, Cloudinary cloudinary, PasswordEncoder passwordEncoder, AuthService authService) {
        this.userRepository = userRepository;
        this.cloudinary = cloudinary;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    public UserDto updateProfile(ProfileUpdateRequest request) {
        User user = getCurrentUser();

        user.setFirstName(request.getFirstName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setHomeAddress(request.getHomeAddress());
        user.setWastePreferences(request.getWastePreferences());
        user.setCollectionSchedule(request.getCollectionSchedule());

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

    public List<UserDto> getAllUsers(String search) {
        List<User> users = search == null || search.isBlank()
                ? userRepository.findAll()
                : userRepository.searchUsers(search);

        return users.stream()
                .map(u -> modelMapper.map(u, UserDto.class))
                .collect(Collectors.toList());
    }

    public UserDto createUser(User user) {
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userRepository.save(user);
        return modelMapper.map(user, UserDto.class);
    }

    public UserDto updateUser(String id, User update) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException("User not found"));

        if (update.getFirstName() != null) user.setFirstName(update.getFirstName());
        if (update.getMiddleName() != null) user.setMiddleName(update.getMiddleName());
        if (update.getLastName() != null) user.setLastName(update.getLastName());
        if (update.getPhoneNumber() != null) user.setPhoneNumber(update.getPhoneNumber());
        if (update.getHomeAddress() != null) user.setHomeAddress(update.getHomeAddress());
        if (update.getRole() != null) user.setRole(update.getRole());

        userRepository.save(user);
        return modelMapper.map(user, UserDto.class);
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    public void archiveUser(String id) {
        updateUserStatus(id, u -> u.setArchived(true));
    }

    public void unarchiveUser(String id) {
        updateUserStatus(id, u -> u.setArchived(false));
    }

    public void activateUser(String id) {
        updateUserStatus(id, u -> u.setActive(true));
    }

    public void deactivateUser(String id) {
        updateUserStatus(id, u -> u.setActive(false));
    }

    public void verifyUser(String id) {
        updateUserStatus(id, u -> u.setVerified(true));
    }

    public void unverifyUser(String id) {
        updateUserStatus(id, u -> u.setVerified(false));
    }

    private void updateUserStatus(String id, java.util.function.Consumer<User> action) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException("User not found"));
        action.accept(user);
        userRepository.save(user);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException("No authenticated user");
        }
        return (User) authentication.getPrincipal();
    }
}