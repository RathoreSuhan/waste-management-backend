package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * ============================================================================
 * Location Directory
 * ============================================================================
 *
 * GET /api/locations -> the state / district / urban-local-body directory the
 * sign-up form is built from.
 *
 * Public, because registration is: SecurityConfig lists the path in its
 * permitAll block, which grants nothing beyond reading this file.
 *
 * The browser needs all of it at once. There are roughly 5,000 ULB records and
 * the cascade filters them locally on every keystroke, which is far cheaper
 * than three round trips per sign-up and keeps the dependent fields responsive
 * on a slow connection.
 *
 * Cached for a day, both here and at the browser: the directory only changes
 * when a new LGD workbook is imported and the application restarted, so
 * refetching it within a session is wasted bytes. The response is built once
 * at startup (see IndianLocationService#directory).
 * ============================================================================
 */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final IndianLocationService indianLocationService;

    @GetMapping
    public ResponseEntity<JsonNode> directory() {

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(indianLocationService.directory());
    }
}
