package com.cleanbharat.wastemanagement.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ReportResponse {

    private Long id; // report id

    private String title; // report title

    private String description; // report details

    private String location; // area name

    private String imageUrl; // image path/url

    private String status; // current status

    private String reportedBy; // citizen name

    private LocalDateTime createdAt; // creation time
}