package com.cleanbharat.wastemanagement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;

@Service
@RequiredArgsConstructor
public class ImageDownloadServiceImpl implements ImageDownloadService {

    @Override
    public byte[] downloadImage(String imageUrl) {

        try (
             InputStream inputStream = URI.create(imageUrl)
                                          .toURL()
                                          .openStream()
        ) {

            // Read entire image
            return inputStream.readAllBytes();

        } catch (Exception ex) {
            throw new RuntimeException("Unable to download image.", ex);
        }
    }
}