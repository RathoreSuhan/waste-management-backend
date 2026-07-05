package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Fetch all comments belonging to a report
    List<Comment> findByReportId(Long reportId);

    // Fetch only top-level comments of a report
    @Query("""
        SELECT DISTINCT c
        FROM Comment c
        LEFT JOIN FETCH c.user
        LEFT JOIN FETCH c.replies r
        LEFT JOIN FETCH r.user
        WHERE c.report.id = :reportId
          AND c.parentComment IS NULL
        ORDER BY c.createdAt ASC
    """)
    List<Comment> findCommentTreeByReportId(@Param("reportId") Long reportId);

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