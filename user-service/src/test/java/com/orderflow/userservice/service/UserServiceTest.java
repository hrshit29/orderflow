package com.orderflow.userservice.service;

import com.orderflow.events.UserRegisteredEvent;
import com.orderflow.userservice.dto.UserDtos.*;
import com.orderflow.userservice.exception.Exceptions.*;
import com.orderflow.userservice.model.User;
import com.orderflow.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository repo;
    private ApplicationEventPublisher publisher;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new UserService(repo, encoder, publisher);
    }

    @Test
    void registerHashesPasswordAndPublishesEvent() {
        when(repo.existsByEmailIgnoreCase("a@b.com")).thenReturn(false);
        when(repo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            u.setCreatedAt(Instant.now());
            return u;
        });

        UserResponse res = service.register(new RegisterRequest("A@b.com", "Asha", null, "password123"));

        assertThat(res.id()).isEqualTo(1L);
        assertThat(res.email()).isEqualTo("a@b.com");
        verify(repo).save(argThat(u -> encoder.matches("password123", u.getPasswordHash())));
        verify(publisher).publishEvent(any(UserRegisteredEvent.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(repo.existsByEmailIgnoreCase("a@b.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("a@b.com", "A", null, "password123")))
                .isInstanceOf(DuplicateResourceException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void loginFailsOnWrongPassword() {
        User u = User.builder().id(1L).email("a@b.com").passwordHash(encoder.encode("right-pass"))
                .role(User.Role.CUSTOMER).build();
        when(repo.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "wrong-pass")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
