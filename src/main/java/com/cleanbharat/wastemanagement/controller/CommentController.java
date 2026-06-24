package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.CommentRequest;
import com.cleanbharat.wastemanagement.dto.CommentResponse;
import com.cleanbharat.wastemanagement.dto.ReplyRequest;
import com.cleanbharat.wastemanagement.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/report/{reportId}")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long reportId, @RequestBody CommentRequest request){
        CommentResponse response = commentService.addComment(reportId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/{commentId}/reply")
    public ResponseEntity<CommentResponse> addReply(@PathVariable Long commentId, @RequestBody ReplyRequest request) {
        CommentResponse response = commentService.addReply(commentId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/report/{reportId}")
    public ResponseEntity<List<CommentResponse>> getCommentsByReport(@PathVariable Long reportId) {
        return ResponseEntity.ok(commentService.getCommentsByReport(reportId));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<String> deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.ok("Comment deleted successfully");
    }
}