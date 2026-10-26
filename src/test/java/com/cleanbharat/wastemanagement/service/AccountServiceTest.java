package com.cleanbharat.wastemanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cleanbharat.wastemanagement.dto.ChangePasswordRequest;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.dto.UpdateProfileRequest;
import com.cleanbharat.wastemanagement.dto.admin.UserDetailsResponse;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.InvalidPasswordChangeException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.CommentRepository;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;
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
    private static final String MUNICIPAL_EMAIL = "mc@mohali.gov.in"; // Corporation login email registered by the admin
    private static final String ENCODED_CURRENT_PASSWORD = "encoded-current-password";

    @Mock
    private UserRepository userRepository;

    // Municipal corporations are a second login identity, so the service needs their repository too
    @Mock
    private MunicipalCorporationRepository municipalCorporationRepository;

    @Mock
    private GarbageReportRepository garbageReportRepository;

    @Mock
    private CleanupAssignmentRepository cleanupAssignmentRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CloudinaryService cloudinaryService;

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
        // Neither a user nor a municipal corporation owns this email
        when(municipalCorporationRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.changePassword(EMAIL, request)
        );

        assertEquals("User not found", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePasswordUpdatesMunicipalCorporationPassword() {
        // A municipal corporation must be able to move away from the default mc123456 password
        MunicipalCorporation corporation = MunicipalCorporation.builder()
                .city("Mohali")
                .organizationName("Municipal Corporation SAS Nagar Mohali")
                .email(MUNICIPAL_EMAIL)
                .password(ENCODED_CURRENT_PASSWORD)
                .build();
        ChangePasswordRequest request = new ChangePasswordRequest(
                "current-password",
                "new-password",
                "new-password"
        );

        when(userRepository.findByEmail(MUNICIPAL_EMAIL)).thenReturn(Optional.empty());
        when(municipalCorporationRepository.findByEmailIgnoreCase(MUNICIPAL_EMAIL))
                .thenReturn(Optional.of(corporation));
        when(passwordEncoder.matches("current-password", ENCODED_CURRENT_PASSWORD))
                .thenReturn(true);
        when(passwordEncoder.matches("new-password", ENCODED_CURRENT_PASSWORD))
                .thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");

        SuccessResponse response = accountService.changePassword(MUNICIPAL_EMAIL, request);

        assertEquals("encoded-new-password", corporation.getPassword());
        assertEquals("Password changed successfully", response.getMessage());
        verify(municipalCorporationRepository).save(corporation);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deleteProfilePictureClearsTheStoredUrlOnlyAfterCloudinaryDeletionSucceeds() {
        String profilePicUrl = "https://res.cloudinary.com/demo/image/upload/v1/profile-picture.jpg";
        User user = User.builder()
                .email(EMAIL)
                .profilePic(profilePicUrl)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        SuccessResponse response = accountService.deleteProfilePicture(EMAIL);

        assertEquals(null, user.getProfilePic());
        assertEquals("Profile picture removed successfully", response.getMessage());
        verify(cloudinaryService).deleteFileOrThrow(profilePicUrl);
        verify(userRepository).save(user);
    }

    @Test
    void deleteProfilePictureDoesNotClearOrSaveWhenCloudinaryDeletionFails() {
        String profilePicUrl = "https://res.cloudinary.com/demo/image/upload/v1/profile-picture.jpg";
        User user = User.builder()
                .email(EMAIL)
                .profilePic(profilePicUrl)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        RuntimeException cloudinaryFailure = new RuntimeException("Cloudinary delete failed");
        org.mockito.Mockito.doThrow(cloudinaryFailure)
                .when(cloudinaryService)
                .deleteFileOrThrow(profilePicUrl);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> accountService.deleteProfilePicture(EMAIL)
        );

        assertEquals(cloudinaryFailure, exception);
        assertEquals(profilePicUrl, user.getProfilePic());
        verify(cloudinaryService).deleteFileOrThrow(profilePicUrl);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateProfilePersistsReplacementBeforeBestEffortOldImageCleanup() {
        String oldProfilePicUrl = "https://res.cloudinary.com/demo/image/upload/v1/old-profile-picture.jpg";
        String newProfilePicUrl = "https://res.cloudinary.com/demo/image/upload/v2/new-profile-picture.jpg";
        User user = User.builder()
                .email(EMAIL)
                .name("Original Name")
                .profilePic(oldProfilePicUrl)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        SuccessResponse response = accountService.updateProfile(
                EMAIL,
                new UpdateProfileRequest("Updated Name", newProfilePicUrl)
        );

        assertEquals("Updated Name", user.getName());
        assertEquals(newProfilePicUrl, user.getProfilePic());
        assertEquals("Profile updated successfully", response.getMessage());
        verify(userRepository).save(user);
        verify(cloudinaryService).deleteFile(oldProfilePicUrl);
        verify(cloudinaryService, never()).deleteFileOrThrow(oldProfilePicUrl);
    }

    @Test
    void getAccountRecordReturnsOnlyTheAuthenticatedUsersIdentityAndActivity() {
        User user = User.builder()
                .id(24L)
                .name("Suhan Kumar Singh")
                .email(EMAIL)
                .role(Role.ROLE_CITIZEN)
                .state("Bihar")
                .city("Gaya")
                .rewardPoints(45)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(cleanupAssignmentRepository.countByCleanerAndStatus(user, AssignmentStatus.COMPLETED))
                .thenReturn(0L);
        when(garbageReportRepository.countByUser(user)).thenReturn(3L);
        when(commentRepository.countByUser(user)).thenReturn(4L);
        when(voteRepository.countByUserAndRatingIsNotNull(user)).thenReturn(2L);

        UserDetailsResponse response = accountService.getAccountRecord(EMAIL);

        assertEquals(24L, response.getId());
        assertEquals("Suhan Kumar Singh", response.getName());
        assertEquals(3L, response.getReportsCreated());
        assertEquals(4L, response.getComments());
        assertEquals(2L, response.getVotes());
        assertEquals(45, response.getRewardPoints());
    }
}
