package com.orderflow.userservice.service;

import com.orderflow.events.PageResponse;
import com.orderflow.events.UserRegisteredEvent;
import com.orderflow.userservice.dto.UserDtos.*;
import com.orderflow.userservice.exception.Exceptions.*;
import com.orderflow.userservice.model.User;
import com.orderflow.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UserResponse register(RegisterRequest req) {
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw new DuplicateResourceException("Email already registered: " + req.email());
        }
        User user = userRepository.save(User.builder()
                .email(req.email().toLowerCase())
                .fullName(req.fullName())
                .phone(req.phone())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(User.Role.CUSTOMER)
                .build());

        eventPublisher.publishEvent(
                new UserRegisteredEvent(user.getId(), user.getEmail(), user.getFullName(), Instant.now()));
        return UserResponse.from(user);
    }

    /** Credential check only. JWT issuance moves to the auth layer with the API Gateway (Phase 3). */
    @Transactional(readOnly = true)
    public UserResponse login(LoginRequest req) {
        User user = userRepository.findByEmailIgnoreCase(req.email())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, Pageable pageable) {
        Page<User> page = StringUtils.hasText(search)
                ? userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable)
                : userRepository.findAll(pageable);
        return PageResponse.from(page, UserResponse::from);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req) {
        User user = find(id);
        if (StringUtils.hasText(req.fullName())) user.setFullName(req.fullName());
        if (StringUtils.hasText(req.phone())) user.setPhone(req.phone());
        return UserResponse.from(user);
    }

    @Transactional
    public void delete(Long id) {
        userRepository.delete(find(id));
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
