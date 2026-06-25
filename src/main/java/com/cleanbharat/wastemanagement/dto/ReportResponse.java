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

    private String description; // garbage details

    private Double latitude; // GPS latitude

    private Double longitude; // GPS longitude

    private String address; // full address

    private String landmark; // nearby landmark

    private String city; // city name

    private String state; // state name

    private String pincode; // postal code

    private String imageUrl; // cloudinary image url

    private String status; // report status

    private Double urgencyScore; // Average citizen rating (1-5)

    private Double engagementScore; // report popularity score

    private String reportedBy; // citizen name

    private LocalDateTime createdAt; // report creation time
}