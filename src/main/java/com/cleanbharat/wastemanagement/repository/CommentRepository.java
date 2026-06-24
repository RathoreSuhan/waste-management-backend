package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    // Fetch all comments belonging to a report
    List<Comment> findByReportId(Long reportId);

    // Fetch only top-level comments of a report
    List<Comment> findByReportIdAndParentCommentIsNull(Long reportId);
}