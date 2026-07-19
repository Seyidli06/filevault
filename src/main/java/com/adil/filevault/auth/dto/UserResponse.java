package com.adil.filevault.auth.dto;

import com.adil.filevault.auth.security.AuthenticatedUser;
import com.adil.filevault.user.entity.Role;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        Role role
) {

    public static UserResponse from(
            AuthenticatedUser user
    ) {
        return new UserResponse(
                user.id(),
                user.fullName(),
                user.getUsername(),
                user.role()
        );
    }
}