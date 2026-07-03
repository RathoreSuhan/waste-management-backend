package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.PublicFeedResponse;
import com.cleanbharat.wastemanagement.service.PublicFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/public-feed")
@RequiredArgsConstructor
public class PublicFeedController {

    // Public Feed Service
    private final PublicFeedService publicFeedService;

    /**
     * Returns all completed AI-verified cleanups.
     */
    @GetMapping
    public ResponseEntity<List<PublicFeedResponse>> getPublicFeed() {
        List<PublicFeedResponse> response = publicFeedService.getPublicFeed();
        return ResponseEntity.ok(response);
    }

    /**
     * Returns one completed cleanup by report ID.
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<PublicFeedResponse> getPublicFeedByReportId(@PathVariable Long reportId) {
        PublicFeedResponse response = publicFeedService.getPublicFeedByReportId(reportId);
        return ResponseEntity.ok(response);
    }
}