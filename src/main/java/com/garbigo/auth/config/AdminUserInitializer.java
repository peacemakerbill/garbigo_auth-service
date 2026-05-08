package com.garbigo.auth.config;

import com.garbigo.auth.model.Role;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String adminEmail = "admin@garbigo.com";
        
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }
        
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setUsername("admin");
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setPassword(passwordEncoder.encode("garbiadmin!"));
        admin.setRole(Role.ADMIN);
        admin.setVerified(true);
        admin.setActive(true);
        admin.setArchived(false);
        
        userRepository.save(admin);
    }
}