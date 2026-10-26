package com.cleanbharat.wastemanagement.service;

import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {

    // Upload image to Cloudinary and return image URL
    String uploadFile(MultipartFile file);

    // Delete image from Cloudinary using its URL
    void deleteFile(String imageUrl);

    /**
     * Deletes an image only when Cloudinary confirms that the asset was removed.
     *
     * Unlike {@link #deleteFile(String)}, this method throws when the URL cannot
     * be parsed, Cloudinary reports any result other than "ok", or its API call
     * fails. Use it when the caller must not clear its database reference unless
     * the Cloudinary asset has been deleted.
     */
    void deleteFileOrThrow(String imageUrl);
}