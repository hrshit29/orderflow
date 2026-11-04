package com.orderflow.userservice.controller;

import com.orderflow.events.Caller;
import com.orderflow.events.PageResponse;
import com.orderflow.userservice.dto.UserDtos.*;
import com.orderflow.userservice.exception.Exceptions.ResourceNotFoundException;
import com.orderflow.userservice.security.JwtService;
import com.orderflow.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Registration, login (JWT) and profile management")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user (publishes user-registered)")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in; returns a JWT to send as 'Authorization: Bearer <token>'")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        UserResponse user = userService.login(request);
        return new LoginResponse(jwtService.issue(user), "Bearer", jwtService.ttlSeconds(), user);
    }

    @GetMapping("/me")
    @Operation(summary = "The logged-in user's profile")
    public UserResponse me(@Parameter(hidden = true) @RequestHeader(Caller.USER_ID_HEADER) Long userId) {
        return userService.getById(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by id (own profile, or any as admin)")
    public UserResponse get(@PathVariable Long id,
                            @Parameter(hidden = true) @RequestHeader(value = Caller.USER_ID_HEADER, required = false) Long callerId,
                            @Parameter(hidden = true) @RequestHeader(value = Caller.ROLE_HEADER, required = false) String role) {
        checkOwner(id, Caller.of(callerId, role));
        return userService.getById(id);
    }

    @GetMapping
    @Operation(summary = "List users (admin only; paginated, optional search on name/email)")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.list(search, pageable);   // ADMIN role enforced at the gateway
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update profile (own, or any as admin)")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
                               @Parameter(hidden = true) @RequestHeader(value = Caller.USER_ID_HEADER, required = false) Long callerId,
                               @Parameter(hidden = true) @RequestHeader(value = Caller.ROLE_HEADER, required = false) String role) {
        checkOwner(id, Caller.of(callerId, role));
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete user (own account, or any as admin)")
    public void delete(@PathVariable Long id,
                       @Parameter(hidden = true) @RequestHeader(value = Caller.USER_ID_HEADER, required = false) Long callerId,
                       @Parameter(hidden = true) @RequestHeader(value = Caller.ROLE_HEADER, required = false) String role) {
        checkOwner(id, Caller.of(callerId, role));
        userService.delete(id);
    }

    /** 404 rather than 403, so customers can't probe which user ids exist. */
    private static void checkOwner(Long id, Caller caller) {
        if (!caller.owns(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
    }
}
