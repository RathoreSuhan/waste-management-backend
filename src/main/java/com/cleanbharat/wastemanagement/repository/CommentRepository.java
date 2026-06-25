package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Fetch all comments belonging to a report
    List<Comment> findByReportId(Long reportId);

    // Fetch only top-level comments of a report
    List<Comment> findByReportIdAndParentCommentIsNull(Long reportId);

    // Fetch all comments
    List<Comment> findAll();

    // Count only top-level comments
    @Query("""
            SELECT COUNT(c)
            FROM Comment c
            WHERE c.parentComment IS NULL
            """)
    long countTopLevelComments();

    // Count only replies
    @Query("""
            SELECT COUNT(c)
            FROM Comment c
            WHERE c.parentComment IS NOT NULL
            """)
    long countReplies();

}