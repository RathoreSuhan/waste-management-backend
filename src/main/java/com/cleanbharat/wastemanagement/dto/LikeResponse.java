package com.cleanbharat.wastemanagement.dto;

import lombok.*;

/**
 * Result of appreciating a cleanup, or withdrawing that appreciation.
 *
 * The like endpoint used to reply with only a message, which left the page
 * to guess the new total by adding one to whatever it last displayed. That
 * guess was wrong whenever anyone else had liked the same cleanup in the
 * meantime, and it could not express a like being withdrawn at all.
 *
 * Returning the stored total and this user's own state removes the guess:
 * the page displays what the database actually holds.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LikeResponse {

    // Cleanup that was liked, named so the caller can match the reply
    private Long reportId;

    // Total likes now recorded, counted from the individual like records
    private Long likeCount;

    // Whether the signed-in user's like currently stands
    private Boolean liked;
}
