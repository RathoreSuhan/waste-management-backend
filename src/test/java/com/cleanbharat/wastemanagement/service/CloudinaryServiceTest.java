package com.cleanbharat.wastemanagement.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Cloudinary gateway every image in the cleanup workflow
 * passes through.
 *
 * Why this class matters to the cleanup entities:
 *
 * A cleanup stores several photographs - the citizen's "before" image, the
 * cleaner's inspection photo on a proposal, each activity-log picture and the
 * "after" proof, which is replaced every time the municipality asks for rework.
 * All of those are released by URL, and a URL is only usable to Cloudinary once
 * it has been reduced to a public_id. Derive that id wrongly and Cloudinary
 * answers 200 with {"result":"not found"}: the row goes, the file stays, and
 * nothing anywhere reports a failure.
 *
 * So the two behaviors pinned here are the id derivation itself, and the
 * promise that a delete NEVER throws - the deletion services call it from
 * inside @Transactional, where an exception would roll back database work that
 * had already succeeded.
 */
@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    private static final String SECURE_URL =
            "https://res.cloudinary.com/demo/image/upload/v1712345678/waste-management/abc123.jpg";

    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;

    @InjectMocks private CloudinaryServiceImpl cloudinaryService;

    @Captor private ArgumentCaptor<String> publicIdCaptor;

    // ---------------------------------------------------------------------
    // UPLOAD
    // ---------------------------------------------------------------------

    @Test
    void uploadReturnsTheSecureHttpsUrlCloudinaryAssigned() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of(
                "public_id", "waste-management/abc123",
                "secure_url", SECURE_URL
        ));

        String url = cloudinaryService.uploadFile(proofImage());

        // The secure URL is what gets stored on the assignment / proposal / log row
        assertEquals(SECURE_URL, url);
    }

    @Test
    void anUploadFailureIsReportedWithItsOriginalCause() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenThrow(new IOException("connection reset"));

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> cloudinaryService.uploadFile(proofImage()));

        // Callers roll their transaction back on this, so the reason must survive
        assertNotNull(failure.getCause());
        assertEquals("connection reset", failure.getCause().getMessage());
    }

    // ---------------------------------------------------------------------
    // DELETE: DERIVING THE public_id
    // ---------------------------------------------------------------------

    @Test
    void aFolderedVersionedUrlYieldsTheFolderAndFileWithoutTheExtension() throws IOException {
        String publicId = capturePublicIdFor(SECURE_URL);

        assertEquals("waste-management/abc123", publicId);
    }

    @Test
    void transformationSegmentsBeforeTheVersionAreDiscarded() throws IOException {
        // Delivery URLs built with transformations carry them between /upload/ and the version
        String publicId = capturePublicIdFor(
                "https://res.cloudinary.com/demo/image/upload/f_auto,q_auto/w_800/v1712345678/waste-management/after.png");

        // Keeping "f_auto,q_auto" in the id is what produced silent "not found" results
        assertEquals("waste-management/after", publicId);
    }

    @Test
    void aUrlWithNoVersionSegmentStillKeepsItsFolderPath() throws IOException {
        String publicId = capturePublicIdFor(
                "https://res.cloudinary.com/demo/image/upload/waste-management/proposals/site-visit.jpeg");

        assertEquals("waste-management/proposals/site-visit", publicId);
    }

    @Test
    void aSignedUrlIsReducedToTheIdWithoutItsQueryOrFragment() throws IOException {
        String publicId = capturePublicIdFor(SECURE_URL + "?_a=SIGNATURE&t=1#preview");

        assertEquals("waste-management/abc123", publicId);
    }

    @Test
    void deleteAsksCloudinaryToPurgeTheCdnCopyToo() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);

        ArgumentCaptor<Map<String, Object>> optionsCaptor = optionsCaptor();
        when(uploader.destroy(anyString(), optionsCaptor.capture())).thenReturn(Map.of("result", "ok"));

        cloudinaryService.deleteFile(SECURE_URL);

        Map<String, Object> options = optionsCaptor.getValue();

        // Without invalidate the edge keeps serving a deleted before/after image
        assertEquals(true, options.get("invalidate"));
        assertEquals("image", options.get("resource_type"));
    }

    // ---------------------------------------------------------------------
    // DELETE: WHEN THERE IS NOTHING TO DO
    // ---------------------------------------------------------------------

    @Test
    void nothingIsSentToCloudinaryWhenNoImageWasEverStored() {
        // Inspection photos and activity-log pictures are optional, so null is normal
        cloudinaryService.deleteFile(null);
        cloudinaryService.deleteFile("   ");

        verifyNoInteractions(cloudinary);
    }

    @Test
    void aUrlThatIsNotACloudinaryUploadIsSkippedRatherThanGuessedAt() {
        // No "/upload/" marker means no id can be derived; guessing one could destroy the wrong asset
        cloudinaryService.deleteFile("https://example.test/images/some-other-host.jpg");

        verifyNoInteractions(cloudinary);
    }

    // ---------------------------------------------------------------------
    // DELETE: NEVER THROWS
    //
    // Both deletion services and the proof re-upload path call deleteFile from
    // inside a transaction, so a Cloudinary problem must not become a rollback.
    // ---------------------------------------------------------------------

    @Test
    void anAssetCloudinaryCannotFindDoesNotFailTheCaller() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "not found"));

        // Logged as a warning: the asset survived, but the caller's own work stands
        assertDoesNotThrow(() -> cloudinaryService.deleteFile(SECURE_URL));
    }

    @Test
    void aCloudinaryOutageDoesNotRollBackTheCallersTransaction() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(anyString(), anyMap())).thenThrow(new IOException("cloudinary unreachable"));

        assertDoesNotThrow(() -> cloudinaryService.deleteFile(SECURE_URL));
    }

    @Test
    void anEmptyAnswerFromCloudinaryIsTreatedAsAFailedDeleteNotACrash() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(anyString(), anyMap())).thenReturn(null); // SDK gave us nothing to read

        assertDoesNotThrow(() -> cloudinaryService.deleteFile(SECURE_URL));
    }

    // ---------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------

    private MultipartFile proofImage() {
        return new MockMultipartFile("image", "after.jpg", "image/jpeg", "binary-content".getBytes());
    }

    /*
     * Runs one delete and returns the public_id the service asked Cloudinary to
     * destroy - the value the whole media library depends on being right.
     */
    private String capturePublicIdFor(String imageUrl) throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(publicIdCaptor.capture(), anyMap())).thenReturn(Map.of("result", "ok"));

        cloudinaryService.deleteFile(imageUrl);

        return publicIdCaptor.getValue();
    }

    // The SDK exposes destroy(String, Map) as a raw Map, so the captor is built separately
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> optionsCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
