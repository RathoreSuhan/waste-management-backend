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

        /*
         * Average the ratings only.
         *
         * A row can exist to record that someone appreciated the cleanup
         * and carry no rating at all. Those rows say nothing about how
         * urgent the garbage is, so they are left out: including them
         * would unbox a null rating and fail, and treating an absent
         * rating as zero would drag the average down and misrepresent
         * the urgency this report was actually given.
         */
        double average = votes.stream()
                        .map(Vote::getRating)
                        .filter(rating -> rating != null)
                        .mapToInt(Integer::intValue)
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