package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.PublicFeedResponse;
import com.cleanbharat.wastemanagement.service.PublicFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import java.time.LocalDateTime;
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

    //for increment view api
    @PostMapping("/{reportId}/view")
    public ResponseEntity<SuccessResponse> incrementView(@PathVariable Long reportId) {

        publicFeedService.incrementView(reportId);

        return ResponseEntity.ok(
                SuccessResponse.builder()
                        .message("View recorded successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    //for increment like api
    @PostMapping("/{reportId}/like")
    public ResponseEntity<SuccessResponse> incrementLike(@PathVariable Long reportId){

        publicFeedService.incrementLike(reportId);

        return ResponseEntity.ok(
                SuccessResponse.builder()
                        .message("Like recorded successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    //for increment share api
    @PostMapping("/{reportId}/share")
    public ResponseEntity<SuccessResponse> incrementShare(@PathVariable Long reportId){

        publicFeedService.incrementShare(reportId);

        return ResponseEntity.ok(
                SuccessResponse.builder()
                        .message("Share recorded successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }
}