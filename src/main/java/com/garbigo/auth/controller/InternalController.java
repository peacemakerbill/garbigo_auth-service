package com.garbigo.auth.controller;

import com.garbigo.auth.dto.InternalUserDto;
import com.garbigo.auth.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal")
public class InternalController {

    private final UserService userService;

    public InternalController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<InternalUserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsersInternal());
    }
}