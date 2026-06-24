package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CommentRequest;
import com.cleanbharat.wastemanagement.dto.CommentResponse;
import com.cleanbharat.wastemanagement.dto.ReplyRequest;
import java.util.List;

public interface CommentService {

    // Create top-level comment
    CommentResponse addComment(Long reportId, CommentRequest request);

    // Create reply to existing comment
    CommentResponse addReply(Long commentId, ReplyRequest request);

    // Get all comments of a report
    List<CommentResponse> getCommentsByReport(Long reportId);

    // Delete comment
    void deleteComment(Long commentId);
}