package com.cleanbharat.wastemanagement.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Logger so Cloudinary delete outcomes are no longer invisible
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@Service // Registers this class as a Spring Bean
@RequiredArgsConstructor // Creates constructor for final fields
@Slf4j // Enables "log" for auditing every upload / destroy result
public class CloudinaryServiceImpl implements CloudinaryService {

    // Injected from CloudinaryConfig Bean
    private final Cloudinary cloudinary;

    @Override
    public String uploadFile(MultipartFile file) {
        try {
            // Upload image bytes to Cloudinary
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());

            /*
             * Log the public_id Cloudinary assigned to this asset.
             *
             * Deletion works on public_id (not on the URL), so having it in
             * the logs lets an upload be matched with its later delete attempt.
             */
            log.info("Cloudinary upload succeeded. publicId={}", uploadResult.get("public_id"));

            // Return secure HTTPS image URL
            return uploadResult.get("secure_url").toString();

        } catch (IOException e) {
            // If upload fails
            log.error("Cloudinary upload failed", e); // Record the real reason before wrapping it
            throw new RuntimeException("Failed to upload image", e); // Keep the cause instead of dropping it
        }
    }

    @Override
    public void deleteFile(String imageUrl) {

        // Nothing to destroy when no image was ever stored
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        // Derive the Cloudinary public_id this URL points to
        String publicId = extractPublicId(imageUrl);

        // A URL we cannot parse can never be destroyed, so report it loudly
        if (publicId == null || publicId.isBlank()) {
            log.warn("Cloudinary delete skipped, public_id could not be derived. imageUrl={}", imageUrl);
            return;
        }

        try {
            /*
             * Destroy the asset.
             *
             * invalidate=true also purges the cached CDN copies, otherwise a
             * deleted image keeps being served from the edge for a while.
             *
             * resource_type=image makes the target explicit instead of
             * relying on the SDK default.
             */
            Map destroyResult = cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap(
                            "invalidate", true,      // Clear CDN cached copies as well
                            "resource_type", "image" // All assets uploaded by this app are images
                    )
            );

            /*
             * Cloudinary answers with HTTP 200 and NO exception even when it
             * deleted nothing, reporting {"result":"not found"} instead.
             *
             * Ignoring this value was the reason database rows disappeared
             * while the images stayed in the media library, so the outcome is
             * now inspected explicitly.
             */
            Object result = destroyResult == null ? null : destroyResult.get("result");

            if ("ok".equals(result)) {
                // Asset really was removed from Cloudinary
                log.info("Cloudinary delete succeeded. publicId={}", publicId);
            } else {
                /*
                 * "not found" (or anything else) means the asset survived.
                 *
                 * The derived public_id most likely does not match the stored
                 * one, so both are logged to make the mismatch diagnosable.
                 */
                log.warn("Cloudinary delete did not remove the asset. publicId={} result={} imageUrl={}",
                        publicId, result, imageUrl);
            }

        } catch (Exception e) {
            /*
             * Never rethrow.
             *
             * Callers such as ReportDeletionServiceImpl and
             * AssignmentDeletionServiceImpl run inside @Transactional, so
             * throwing here would roll back the database cleanup that already
             * succeeded. The failure is recorded instead.
             */
            log.error("Cloudinary delete failed. publicId={} imageUrl={}", publicId, imageUrl, e);
        }
    }

    /**
     * Converts a Cloudinary delivery URL into the public_id used for deletion.
     *
     * Example URL:
     * https://res.cloudinary.com/demo/image/upload/v123456/waste-management/abc123.jpg
     *
     * We need:
     * waste-management/abc123
     */
    private String extractPublicId(String imageUrl) {

        // Everything before "/upload/" is delivery info and is not part of the id
        int uploadIndex = imageUrl.indexOf("/upload/");

        // Not a Cloudinary upload URL, so no id can be derived from it
        if (uploadIndex < 0) {
            return null;
        }

        // Keep only the part after "/upload/" ("/upload/" is 8 characters long)
        String path = imageUrl.substring(uploadIndex + 8);

        // Drop any "?signature=..." query string that would corrupt the id
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }

        // Drop any "#fragment" for the same reason
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }

        // Split the remaining path so version / transformation segments can be skipped
        String[] segments = path.split("/");

        // Rebuilds the id from the segments that actually belong to it
        StringBuilder publicId = new StringBuilder();

        // Becomes true once the "v<digits>" version segment has been passed
        boolean versionPassed = false;

        for (int i = 0; i < segments.length; i++) {

            String segment = segments[i];

            // Ignore empty segments produced by double slashes
            if (segment.isEmpty()) {
                continue;
            }

            /*
             * "v1712345678" is the version segment.
             *
             * Everything before it is a transformation such as
             * "f_auto,q_auto" and must be discarded, because including it
             * produced an invalid id and a silent "not found".
             */
            if (!versionPassed && segment.matches("v\\d+")) {
                versionPassed = true;   // Real id starts after this segment
                publicId.setLength(0);  // Throw away any transformation collected so far
                continue;
            }

            // Keep the folder path intact between segments
            if (publicId.length() > 0) {
                publicId.append('/');
            }

            // Last segment carries the file extension, which is not part of the id
            if (i == segments.length - 1) {
                publicId.append(segment.replaceAll("\\.[^./]+$", "")); // Strip extension only
            } else {
                publicId.append(segment); // Folder segment kept as is
            }
        }

        return publicId.toString();
    }
}