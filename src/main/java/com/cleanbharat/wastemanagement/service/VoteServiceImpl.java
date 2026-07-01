package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.VoteRequest;
import com.cleanbharat.wastemanagement.dto.VoteResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.entity.Vote;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.InvalidVoteException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoteServiceImpl implements VoteService {

    private final VoteRepository voteRepository;
    private final GarbageReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    @Override
    public VoteResponse submitVote(VoteRequest request) {

        // Validate rating range
        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new InvalidVoteException(
                    "Rating must be between 1 and 5"
            );
        }

        // Logged-in user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Only citizens can vote
        if (user.getRole() != Role.ROLE_CITIZEN) {
            throw new InvalidVoteException("Only citizens can vote");
        }

        // Find report
        GarbageReport report = reportRepository.findById(request.getReportId())
                        .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        // Check existing vote
        Vote vote = voteRepository
                .findByUserAndReport(user, report)
                .orElse(null);

        // Update existing vote
        if (vote != null) {
            vote.setRating(request.getRating());
        } else {
            vote = Vote.builder()
                    .rating(request.getRating())
                    .user(user)
                    .report(report)
                    .build();
        }

        voteRepository.save(vote);

        // Recalculate urgency score
        List<Vote> votes = voteRepository.findByReport(report);

        double average = votes.stream()
                        .mapToInt(Vote::getRating)
                        .average()
                        .orElse(0.0);

        report.setUrgencyScore(average);

        reportRepository.save(report);

        // Recalculate engagement score
        analyticsService.recalculateEngagementScore(
                report.getId()
        );

        return VoteResponse.builder()
                .reportId(report.getId())
                .rating(vote.getRating())
                .votedBy(user.getName())
                .urgencyScore(report.getUrgencyScore())
                .build();
    }
}