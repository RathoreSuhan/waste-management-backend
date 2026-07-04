package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.PublicFeedResponse;

import java.util.List;

public interface PublicFeedService {

    // Returns all completed AI-verified cleanups
    List<PublicFeedResponse> getPublicFeed();

    // Returns one completed cleanup by report ID
    PublicFeedResponse getPublicFeedByReportId(Long reportId);

    // Increment view count
    void incrementView(Long reportId);

    // Increment like count
    void incrementLike(Long reportId);

    // Increment share count
    void incrementShare(Long reportId);
}