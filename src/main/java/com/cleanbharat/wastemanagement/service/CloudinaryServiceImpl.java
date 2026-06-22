package com.cleanbharat.wastemanagement.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@Service // Registers this class as a Spring Bean
@RequiredArgsConstructor // Creates constructor for final fields
public class CloudinaryServiceImpl implements CloudinaryService {

    // Injected from CloudinaryConfig Bean
    private final Cloudinary cloudinary;

    @Override
    public String uploadFile(MultipartFile file) {
        try {
            // Upload image bytes to Cloudinary
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());

            // Return secure HTTPS image URL
            return uploadResult.get("secure_url").toString();

        } catch (IOException e) {
            // If upload fails
            throw new RuntimeException("Failed to upload image");
        }
    }
}