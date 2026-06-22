package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController // Marks this class as a REST Controller
@RequestMapping("/api/files") // Base URL for file APIs
@RequiredArgsConstructor // Constructor Injection for final fields
public class FileUploadController {
    // Service responsible for uploading files to Cloudinary
    private final CloudinaryService cloudinaryService;

    // Receives uploaded file from form-data request
    @PostMapping("/upload") // POST /api/files/upload
    public String upload(@RequestParam("file") MultipartFile file) {

        // Upload image to Cloudinary and return URL
        return cloudinaryService.uploadFile(file);
    }
}