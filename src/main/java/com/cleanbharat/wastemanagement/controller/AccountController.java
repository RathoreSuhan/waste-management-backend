package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.ChangePasswordRequest;
import com.cleanbharat.wastemanagement.dto.ConfirmPasswordResetRequest;
import com.cleanbharat.wastemanagement.dto.PasswordResetTokenResponse;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.dto.UpdateProfileRequest;
import com.cleanbharat.wastemanagement.dto.UserProfileResponse;
import com.cleanbharat.wastemanagement.dto.VerifyPasswordResetOtpRequest;
import com.cleanbharat.wastemanagement.dto.admin.UserDetailsResponse;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.service.AccountService;
import com.cleanbharat.wastemanagement.service.PasswordRecoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    private final PasswordRecoveryService passwordRecoveryService;

    @PatchMapping("/password")
    public ResponseEntity<SuccessResponse> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        SuccessResponse response = accountService.changePassword(
                authentication.getName(),
                request
        );

        return ResponseEntity.ok(response);
    }

    /*
     * Password recovery for the profile page, for a signed-in user who
     * cannot remember their current password. All three steps resolve the
     * account from the authenticated identity only - there is no email or
     * account field in any of the payloads, so a request can never aim a
     * reset at somebody else's account. Every step is also namespaced by
     * the session's account context (municipal officer vs users-table
     * account), so two accounts sharing an email across the two stores can
     * never consume each other's codes or reset authorizations.
     * SecurityConfig's fall-through rule keeps them authenticated, and the
     * rate limiter counts them in the same strict auth tier as the
     * change-password endpoint itself.
     */

    /** Sends a six-digit recovery code to the authenticated account's email. */
    @PostMapping("/password/recovery/otp")
    public ResponseEntity<SuccessResponse> requestPasswordResetOtp(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                passwordRecoveryService.requestOtp(
                        authentication.getName(),
                        isMunicipalSession(authentication)
                )
        );
    }

    /** Verifies the code and issues the one-time reset token. */
    @PostMapping("/password/recovery/verify")
    public ResponseEntity<PasswordResetTokenResponse> verifyPasswordResetOtp(
            Authentication authentication,
            @Valid @RequestBody VerifyPasswordResetOtpRequest request
    ) {
        return ResponseEntity.ok(
                passwordRecoveryService.verifyOtp(
                        authentication.getName(),
                        isMunicipalSession(authentication),
                        request.getCode()
                )
        );
    }

    /** Spends the token and sets the new password - no current password required. */
    @PostMapping("/password/recovery/confirm")
    public ResponseEntity<SuccessResponse> confirmPasswordReset(
            Authentication authentication,
            @Valid @RequestBody ConfirmPasswordResetRequest request
    ) {
        return ResponseEntity.ok(
                passwordRecoveryService.confirmReset(
                        authentication.getName(),
                        isMunicipalSession(authentication),
                        request
                )
        );
    }

    /**
     * Mirrors how CustomUserDetailsService resolved this session at login: a
     * municipal-officer authority means the account lives in the
     * municipal_corporations table, everyone else in users. Derived from the
     * authenticated authority alone - a client cannot choose it.
     */
    private boolean isMunicipalSession(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        Role.ROLE_MUNICIPAL_OFFICER.name().equals(authority.getAuthority()));
    }

    /**
     * Uploads a profile picture to Cloudinary and returns the secure URL.
     * The caller passes this URL to {@link #updateProfile} as profilePic.
     */
    @PostMapping("/profile-picture")
    public ResponseEntity<SuccessResponse> uploadProfilePicture(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        String secureUrl = accountService.uploadProfilePicture(file);

        return ResponseEntity.ok(
                SuccessResponse.builder()
                        .message(secureUrl)
                        .timestamp(java.time.LocalDateTime.now())
                        .build()
        );
    }

    /**
     * Returns the profile details of the currently authenticated user.
     * profilePic is null when no profile picture has been uploaded.
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(
            Authentication authentication
    ) {
        UserProfileResponse response = accountService.getProfile(
                authentication.getName()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Returns the signed-in user's own identity and platform activity.
     * This endpoint deliberately derives the account from the JWT principal,
     * rather than accepting a user ID as the admin record endpoint does.
     */
    @GetMapping("/record")
    public ResponseEntity<UserDetailsResponse> getOwnAccountRecord(
            Authentication authentication
    ) {
        return ResponseEntity.ok(accountService.getAccountRecord(authentication.getName()));
    }

    /**
     * Deletes the profile picture of the currently authenticated user.
     * Removes the image from Cloudinary and sets profilePic to null.
     */
    @DeleteMapping("/profile-picture")
    public ResponseEntity<SuccessResponse> deleteProfilePicture(
            Authentication authentication
    ) {
        SuccessResponse response = accountService.deleteProfilePicture(
                authentication.getName()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Updates the profile of the currently authenticated user.
     * Accepts an optional name change and an optional profile picture upload.
     */
    @PutMapping("/profile")
    public ResponseEntity<SuccessResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        SuccessResponse response = accountService.updateProfile(
                authentication.getName(),
                request
        );

        return ResponseEntity.ok(response);
    }
}