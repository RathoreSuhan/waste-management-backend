package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.LikeResponse;
import com.cleanbharat.wastemanagement.dto.PublicFeedResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;


public interface PublicFeedService {

    /**
     * One page of completed AI-verified cleanups, newest first.
     *
     * The feed was returned whole. It is now cut into pages on the server,
     * so a visitor reads ten stories rather than downloading every cleanup
     * ever verified.
     *
     * @param page zero-based page index
     * @param size rows per page, clamped by PaginationUtil
     */
    PageResponse<PublicFeedResponse> getPublicFeed(int page, int size);

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