package com.cleanbharat.wastemanagement.util;

import lombok.experimental.UtilityClass;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Iterator;

/**
 * Utility methods for image processing.
 */
@UtilityClass
public class ImageUtil {

    /**
     * Detects MIME type from image bytes.
     */
    public String detectMimeType(byte[] imageBytes) {

        try (
                ImageInputStream imageInputStream =
                        ImageIO.createImageInputStream(
                                new ByteArrayInputStream(imageBytes))
        ) {

            // Find suitable image reader
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);

            // No reader means invalid image
            if (!readers.hasNext()) {
                return "application/octet-stream";
            }

            ImageReader reader = readers.next();

            // Image format (jpeg/png/webp...)
            String format = reader.getFormatName().toLowerCase();

            return switch (format) {

                case "jpg", "jpeg" -> "image/jpeg";

                case "png" -> "image/png";

                case "webp" -> "image/webp";

                case "bmp" -> "image/bmp";

                default -> "application/octet-stream";
            };

        } catch (Exception ex) {
            return "application/octet-stream";
        }
    }

    /**
     * Converts image bytes into Base64.
     * Gemini Vision accepts images in Base64 format.
     */
    public String convertToBase64(byte[] imageBytes) {
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * Checks whether the MIME type
     * is supported by our AI pipeline.
     */
    public boolean isSupportedMimeType(String mimeType) {

        return switch (mimeType) {

            case "image/jpeg",
                 "image/png",
                 "image/webp" -> true;

            default -> false;
        };
    }

}