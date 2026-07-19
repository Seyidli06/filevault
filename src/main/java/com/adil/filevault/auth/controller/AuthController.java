package com.adil.filevault.auth.controller;

import com.adil.filevault.auth.dto.AuthResponse;
import com.adil.filevault.auth.dto.LoginRequest;
import com.adil.filevault.auth.dto.RegisterRequest;
import com.adil.filevault.auth.dto.UserResponse;
import com.adil.filevault.auth.security.AuthenticatedUser;
import com.adil.filevault.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse currentUser(
            @AuthenticationPrincipal
            AuthenticatedUser authenticatedUser
    ) {
        return UserResponse.from(authenticatedUser);
    }
}