package com.cleanbharat.wastemanagement.util;

import lombok.experimental.UtilityClass;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Iterator;

/**
 * Utility methods for image processing.
 */
@UtilityClass
public class ImageUtils {

    /**
     * Detects MIME type from image bytes.
     */
    public String detectMimeType(byte[] imageBytes) {

        try (
                ImageInputStream imageInputStream =
                        ImageIO.createImageInputStream(
                                new ByteArrayInputStream(imageBytes)
                        )
        ) {

            // Find suitable image reader
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);

            // No reader means invalid image
            if (!readers.hasNext()) {
                return "application/octet-stream";
            }

            ImageReader reader = readers.next();

            // Format name (jpeg/png/webp...)
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

}