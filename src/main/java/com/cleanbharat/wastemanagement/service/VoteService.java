package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.VoteRequest;
import com.cleanbharat.wastemanagement.dto.VoteResponse;

public interface VoteService {

    // Citizen submits vote on a garbage report
    VoteResponse submitVote(VoteRequest request);
}