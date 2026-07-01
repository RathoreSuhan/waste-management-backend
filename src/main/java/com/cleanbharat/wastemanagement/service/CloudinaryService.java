package com.cleanbharat.wastemanagement.service;

import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {

    // Upload image to Cloudinary and return image URL
    String uploadFile(MultipartFile file);

    // Delete image from Cloudinary using its URL
    void deleteFile(String imageUrl);
}