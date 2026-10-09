package com.orderflow.userservice.dto;

import com.orderflow.userservice.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 100) String fullName,
            @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "must be 10-15 digits") String phone,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UpdateUserRequest(
            @Size(max = 100) String fullName,
            @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "must be 10-15 digits") String phone) {
    }

    public record UserResponse(Long id, String email, String fullName, String phone,
                               String role, Instant createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                    u.getRole().name(), u.getCreatedAt());
        }
    }
}
