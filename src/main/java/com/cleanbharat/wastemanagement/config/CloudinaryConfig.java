package com.cleanbharat.wastemanagement.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration // Marks this class as a Spring Configuration class
public class CloudinaryConfig {

    // Reads cloud name from application.properties
    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    // Reads API key from application.properties
    @Value("${cloudinary.api-key}")
    private String apiKey;

    // Reads API secret from application.properties
    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    @Bean // Creates a Cloudinary Bean managed by Spring IOC Container
    public Cloudinary cloudinary() {

        return new Cloudinary(
                ObjectUtils.asMap(
                        // Cloudinary account details
                        "cloud_name", cloudName,
                        "api_key", apiKey,
                        "api_secret", apiSecret
                )
        );
    }
}