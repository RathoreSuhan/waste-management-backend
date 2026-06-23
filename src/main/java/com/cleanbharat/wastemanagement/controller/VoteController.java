package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.VoteRequest;
import com.cleanbharat.wastemanagement.dto.VoteResponse;
import com.cleanbharat.wastemanagement.service.VoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/votes")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;

    @PostMapping
    public ResponseEntity<VoteResponse> submitVote(@RequestBody VoteRequest request){
        VoteResponse response = voteService.submitVote(request);
        return ResponseEntity.ok(response);
    }
}