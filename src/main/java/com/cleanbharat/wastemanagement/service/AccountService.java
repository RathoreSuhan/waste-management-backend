package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.ChangePasswordRequest;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.dto.UpdateProfileRequest;
import com.cleanbharat.wastemanagement.dto.UserProfileResponse;
import com.cleanbharat.wastemanagement.dto.admin.UserDetailsResponse;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.exception.InvalidPasswordChangeException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.CommentRepository;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final MunicipalCorporationRepository municipalCorporationRepository; // municipal bodies live in their own table
    private final GarbageReportRepository garbageReportRepository;
    private final CleanupAssignmentRepository cleanupAssignmentRepository;
    private final CommentRepository commentRepository;
    private final VoteRepository voteRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService; // reuses existing Cloudinary config for profile pic storage

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

    /**
     * Returns the profile of the authenticated user identified by email.
     *
     * @param email the authenticated user's email (from the JWT principal)
     * @return UserProfileResponse with name, email, profilePic (nullable) and role
     * @throws ResourceNotFoundException when no user row matches the email
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return UserProfileResponse.builder()
                .name(user.getName())
                .email(user.getEmail())
                .profilePic(user.getProfilePic())
                .role(user.getRole().name())
                .build();
    }

    /**
     * Returns the current user's read-only account record. The activity
     * definitions match the administrator's account view exactly.
     */
    @Transactional(readOnly = true)
    public UserDetailsResponse getAccountRecord(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        long completedCleanups = cleanupAssignmentRepository.countByCleanerAndStatus(
                user,
                AssignmentStatus.COMPLETED
        );

        return UserDetailsResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .cleanerType(user.getCleanerType())
                .organizationName(user.getOrganizationName())
                .state(user.getState())
                .city(user.getCity())
                .district(user.getDistrict())
                .rewardPoints(user.getRewardPoints())
                .completedCleanups(completedCleanups)
                .reportsCreated(garbageReportRepository.countByUser(user))
                .comments(commentRepository.countByUser(user))
                // Likes have no urgency rating, so they are excluded here.
                .votes(voteRepository.countByUserAndRatingIsNotNull(user))
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Updates the profile of the authenticated user identified by email.
     *
     * @param email   the authenticated user's email (from the JWT principal)
     * @param request the optional name and/or profile picture update
     * @return SuccessResponse confirming the update
     * @throws ResourceNotFoundException when no user row matches the email
     */
    @Transactional
    public SuccessResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Update name when provided (optional - blank means keep existing)
        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }

        // Persist the replacement URL before deleting the old asset. Removing it
        // first risks leaving the account with a broken image when the database
        // transaction cannot commit.
        if (request.getProfilePic() != null && !request.getProfilePic().isBlank()) {
            String oldPic = user.getProfilePic();
            user.setProfilePic(request.getProfilePic());
            userRepository.save(user);

            if (oldPic != null && !oldPic.isBlank() && !oldPic.equals(request.getProfilePic())) {
                deleteReplacedProfilePictureAfterCommit(oldPic);
            }

            return SuccessResponse.builder()
                    .message("Profile updated successfully")
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        userRepository.save(user);

        return SuccessResponse.builder()
                .message("Profile updated successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Cloudinary cleanup must happen only after the replacement is durable.
     * A cleanup failure is best-effort and must not undo a valid profile update.
     */
    private void deleteReplacedProfilePictureAfterCommit(String oldPic) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cloudinaryService.deleteFile(oldPic);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cloudinaryService.deleteFile(oldPic);
            }
        });
    }

    /**
     * Removes the profile picture of the authenticated user identified by email.
     * Deletes the image from Cloudinary and sets profilePic to null in the entity.
     *
     * @param email the authenticated user's email (from the JWT principal)
     * @return SuccessResponse confirming the removal
     * @throws ResourceNotFoundException when no user row matches the email
     */
    @Transactional
    public SuccessResponse deleteProfilePicture(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String currentPic = user.getProfilePic();
        if (currentPic != null && !currentPic.isBlank()) {
            cloudinaryService.deleteFileOrThrow(currentPic); // delete from Cloudinary
            user.setProfilePic(null); // clear the link in the entity
            userRepository.save(user);
        }

        return SuccessResponse.builder()
                .message("Profile picture removed successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Uploads a profile picture file to Cloudinary and returns the secure URL.
     * Intended to be called before {@link #updateProfile(String, UpdateProfileRequest)}
     * so the caller can pass the returned URL as the profilePic field.
     *
     * @param file the image file to upload
     * @return the Cloudinary secure URL of the uploaded image
     */
    public String uploadProfilePicture(MultipartFile file) {
        return cloudinaryService.uploadFile(file);
    }
}