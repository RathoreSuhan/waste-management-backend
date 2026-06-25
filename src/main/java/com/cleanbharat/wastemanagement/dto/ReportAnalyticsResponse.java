package com.cleanbharat.wastemanagement.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ReportAnalyticsResponse {

    private Long reportId; // report id

    private Double urgencyScore; // citizen voting score

    private Long commentCount; // total top-level comments

    private Long replyCount; // total replies

    private Long discussionCount; // comments + replies

    private Double engagementScore; // urgency + discussion score
}