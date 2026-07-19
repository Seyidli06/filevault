package com.adil.filevault.auth.service;

import com.adil.filevault.auth.dto.AuthResponse;
import com.adil.filevault.auth.dto.LoginRequest;
import com.adil.filevault.auth.dto.RegisterRequest;
import com.adil.filevault.auth.dto.UserResponse;
import com.adil.filevault.auth.security.AuthenticatedUser;
import com.adil.filevault.auth.security.JwtService;
import com.adil.filevault.exception.EmailAlreadyExistsException;
import com.adil.filevault.user.entity.Role;
import com.adil.filevault.user.entity.User;
import com.adil.filevault.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyExistsException();
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .passwordHash(
                        passwordEncoder.encode(request.password())
                )
                .role(Role.USER)
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);

        AuthenticatedUser principal =
                AuthenticatedUser.from(savedUser);

        return createAuthResponse(principal);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        Authentication authentication =
                authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken
                                .unauthenticated(
                                        normalizedEmail,
                                        request.password()
                                )
                );

        AuthenticatedUser principal =
                (AuthenticatedUser) authentication.getPrincipal();

        return createAuthResponse(principal);
    }

    private AuthResponse createAuthResponse(
            AuthenticatedUser principal
    ) {
        String accessToken =
                jwtService.generateToken(principal);

        return new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds(),
                UserResponse.from(principal)
        );
    }

    private String normalizeEmail(String email) {
        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}