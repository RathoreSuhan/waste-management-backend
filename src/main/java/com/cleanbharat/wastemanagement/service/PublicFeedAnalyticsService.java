package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.PublicFeedAnalytics;
import com.cleanbharat.wastemanagement.entity.User;

public interface PublicFeedAnalyticsService {

    // Create analytics after successful cleanup
    void initializeAnalytics(CleanupAssignment assignment);

    // Increase view count
    void incrementViewCount(CleanupAssignment assignment);

    /*
      Record or withdraw one user's like, and return the new total.

      This replaces a plain "increase the like count" step. That step could
      not tell one person liking twice from two people liking once, so a
      single visitor could raise the count without limit. Naming the user
      makes the difference expressible, and the like records themselves
      hold the answer.

      Returns true when the user's like now stands, false when it was
      withdrawn.
     */
    boolean toggleLike(CleanupAssignment assignment, User user);

    // Whether this user's like of the cleanup currently stands
    boolean hasLiked(CleanupAssignment assignment, User user);

    // Increase share count
    void incrementShareCount(CleanupAssignment assignment);

    // Fetch analytics of a completed cleanup
    PublicFeedAnalytics getAnalytics(CleanupAssignment assignment);
}


