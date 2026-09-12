package com.cleanbharat.wastemanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients     // Enables Feign Clients
@EnableCaching		// Enables caching for the application
public class WastemanagementApplication {
	public static void main(String[] args) {
		SpringApplication.run(WastemanagementApplication.class, args);
	}
}
