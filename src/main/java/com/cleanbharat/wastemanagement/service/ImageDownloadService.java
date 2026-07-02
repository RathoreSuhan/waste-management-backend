package com.cleanbharat.wastemanagement.service;

public interface ImageDownloadService {

    // Downloads image bytes from URL
    byte[] downloadImage(String imageUrl);
}