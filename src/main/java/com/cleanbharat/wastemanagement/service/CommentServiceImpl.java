package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CommentRequest;
import com.cleanbharat.wastemanagement.dto.CommentResponse;
import com.cleanbharat.wastemanagement.dto.ReplyRequest;
import com.cleanbharat.wastemanagement.entity.Comment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.CommentNotFoundException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.exception.UnauthorizedCommentDeletionException;
import com.cleanbharat.wastemanagement.repository.CommentRepository;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final GarbageReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    @Override
    public CommentResponse addComment(Long reportId, CommentRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        GarbageReport report = reportRepository.findById(reportId)
                        .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        Comment comment = Comment.builder()
                .message(request.getMessage()) // comment text
                .user(user) // comment owner
                .report(report) // related report
                .build();

        Comment savedComment = commentRepository.save(comment);

        // Recalculate engagement score
        analyticsService.recalculateEngagementScore(
                report.getId()
        );

        return mapToResponse(savedComment);
    }

    @Override
    public CommentResponse addReply(Long commentId, ReplyRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Comment parentComment = commentRepository.findById(commentId)
                        .orElseThrow(() -> new CommentNotFoundException("Comment not found"));

        Comment reply = Comment.builder()
                .message(request.getMessage()) // reply text
                .user(user) // reply owner
                .report(parentComment.getReport()) // same report
                .parentComment(parentComment) // parent comment
                .build();

        Comment savedReply = commentRepository.save(reply);

        // Recalculate engagement score
        analyticsService.recalculateEngagementScore(
                parentComment.getReport().getId()
        );

        return mapToResponse(savedReply);
    }

    @Override
    public List<CommentResponse> getCommentsByReport(Long reportId) {

        GarbageReport report = reportRepository.findById(reportId)
                        .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        return commentRepository
                .findByReportIdAndParentCommentIsNull(report.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void deleteComment(Long commentId) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User loggedInUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Comment comment = commentRepository.findById(commentId)
                        .orElseThrow(() -> new CommentNotFoundException("Comment not found"));

        // Admin can delete any comment
        if (loggedInUser.getRole() == Role.ROLE_ADMIN) {
            Long reportId = comment.getReport().getId();
            commentRepository.delete(comment);
            analyticsService.recalculateEngagementScore(
                    reportId
            );
            return;
        }

        // Citizen/Cleaner can delete only own comments
        if (!comment.getUser().getId().equals(loggedInUser.getId())) {
            throw new UnauthorizedCommentDeletionException(
                    "You are not allowed to delete this comment"
            );
        }

        Long reportId = comment.getReport().getId();
        commentRepository.delete(comment);
        analyticsService.recalculateEngagementScore(
                reportId
        );
    }

    private CommentResponse mapToResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId()) // comment id
                .message(comment.getMessage()) // comment text
                .userName(comment.getUser().getName()) // username
                .createdAt(comment.getCreatedAt()) // creation time

                // Recursive mapping for replies
                .replies(
                        comment.getReplies()
                                .stream()
                                .map(this::mapToResponse)
                                .toList()
                )
                .build();
    }
}