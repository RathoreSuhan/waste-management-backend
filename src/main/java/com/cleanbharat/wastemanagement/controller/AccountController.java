package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.ChangePasswordRequest;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.dto.UpdateProfileRequest;
import com.cleanbharat.wastemanagement.dto.UserProfileResponse;
import com.cleanbharat.wastemanagement.dto.admin.UserDetailsResponse;
import com.cleanbharat.wastemanagement.service.AccountService;
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