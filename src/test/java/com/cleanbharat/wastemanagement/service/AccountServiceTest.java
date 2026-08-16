package com.cleanbharat.wastemanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cleanbharat.wastemanagement.dto.ChangePasswordRequest;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.exception.InvalidPasswordChangeException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String EMAIL = "citizen@example.com";
    private static final String ENCODED_CURRENT_PASSWORD = "encoded-current-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    @Test
    void changePasswordEncodesAndSavesNewPassword() {
        User user = User.builder()
                .email(EMAIL)
                .password(ENCODED_CURRENT_PASSWORD)
                .build();
        ChangePasswordRequest request = new ChangePasswordRequest(
                "current-password",
                "new-password",
                "new-password"
        );

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", ENCODED_CURRENT_PASSWORD))
                .thenReturn(true);
        when(passwordEncoder.matches("new-password", ENCODED_CURRENT_PASSWORD))
                .thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");

        SuccessResponse response = accountService.changePassword(EMAIL, request);

        assertEquals("encoded-new-password", user.getPassword());
        assertEquals("Password changed successfully", response.getMessage());
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        User user = User.builder()
                .email(EMAIL)
                .password(ENCODED_CURRENT_PASSWORD)
                .build();
        ChangePasswordRequest request = new ChangePasswordRequest(
                "wrong-password",
                "new-password",
                "new-password"
        );

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", ENCODED_CURRENT_PASSWORD))
                .thenReturn(false);

        InvalidPasswordChangeException exception = assertThrows(
                InvalidPasswordChangeException.class,
                () -> accountService.changePassword(EMAIL, request)
        );

        assertEquals("Current password is incorrect", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePasswordRejectsMismatchedConfirmation() {
        User user = User.builder()
                .email(EMAIL)
                .password(ENCODED_CURRENT_PASSWORD)
                .build();
        ChangePasswordRequest request = new ChangePasswordRequest(
                "current-password",
                "new-password",
                "different-password"
        );

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", ENCODED_CURRENT_PASSWORD))
                .thenReturn(true);

        InvalidPasswordChangeException exception = assertThrows(
                InvalidPasswordChangeException.class,
                () -> accountService.changePassword(EMAIL, request)
        );

        assertEquals(
                "New password and confirmation do not match",
                exception.getMessage()
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePasswordRejectsCurrentPasswordReuse() {
        User user = User.builder()
                .email(EMAIL)
                .password(ENCODED_CURRENT_PASSWORD)
                .build();
        ChangePasswordRequest request = new ChangePasswordRequest(
                "current-password",
                "current-password",
                "current-password"
        );

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", ENCODED_CURRENT_PASSWORD))
                .thenReturn(true);

        InvalidPasswordChangeException exception = assertThrows(
                InvalidPasswordChangeException.class,
                () -> accountService.changePassword(EMAIL, request)
        );

        assertEquals(
                "New password must be different from the current password",
                exception.getMessage()
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePasswordRejectsUnknownAuthenticatedUser() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "current-password",
                "new-password",
                "new-password"
        );

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.changePassword(EMAIL, request)
        );

        assertEquals("User not found", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }
}