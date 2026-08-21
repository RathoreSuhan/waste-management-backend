package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.ChangePasswordRequest;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.exception.InvalidPasswordChangeException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final MunicipalCorporationRepository municipalCorporationRepository; // municipal bodies live in their own table
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SuccessResponse changePassword(String email, ChangePasswordRequest request) {
        Optional<User> existingUser = userRepository.findByEmail(email);

        // Citizens, cleaners and the admin keep using the users table
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            validatePasswordChange(request, user.getPassword());

            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

            return success();
        }

        // Otherwise this must be a Municipal Corporation moving off its default password
        MunicipalCorporation corporation = municipalCorporationRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validatePasswordChange(request, corporation.getPassword());

        corporation.setPassword(passwordEncoder.encode(request.getNewPassword()));
        municipalCorporationRepository.save(corporation);

        return success();
    }

    /** Same rules for every account type, checked against the stored hash. */
    private void validatePasswordChange(ChangePasswordRequest request, String currentHash) {
        if (currentHash == null || !passwordEncoder.matches(request.getCurrentPassword(), currentHash)) {
            throw new InvalidPasswordChangeException("Current password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidPasswordChangeException(
                    "New password and confirmation do not match"
            );
        }

        if (passwordEncoder.matches(request.getNewPassword(), currentHash)) {
            throw new InvalidPasswordChangeException(
                    "New password must be different from the current password"
            );
        }
    }

    private SuccessResponse success() {
        return SuccessResponse.builder()
                .message("Password changed successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }
}