package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.client.GeminiFeignClient;
import com.cleanbharat.wastemanagement.config.GeminiConfig;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiContent;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiPart;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class GeminiTestController {

    private final GeminiFeignClient client;

    private final GeminiConfig config;

    @GetMapping
    public Object test() {

        GeminiRequest request =
                GeminiRequest.builder()
                        .contents(
                                List.of(
                                        GeminiContent.builder()
                                                .parts(
                                                        List.of(
                                                                GeminiPart.builder()
                                                                        .text("Say hello.")
                                                                        .build()
                                                        )
                                                )
                                                .build()
                                )
                        )
                        .build();

        return client.generateContent(
                config.getModel(),
                config.getApiKey(),
                request
        );
    }
}