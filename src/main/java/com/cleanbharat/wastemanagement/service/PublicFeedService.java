package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.LikeResponse;
import com.cleanbharat.wastemanagement.dto.PublicFeedResponse;


import java.util.List;

public interface PublicFeedService {

    // Returns all completed AI-verified cleanups
    List<PublicFeedResponse> getPublicFeed();

    // Returns one completed cleanup by report ID
    PublicFeedResponse getPublicFeedByReportId(Long reportId);

    // Increment view count
    void incrementView(Long reportId);

    /*
      Record the signed-in user's like of a cleanup, or withdraw it.

      Requires a signed-in user: a like has to belong to someone for
      "one like per person" to mean anything.
     */
    LikeResponse toggleLike(Long reportId);


    // Increment share count
    void incrementShare(Long reportId);
}