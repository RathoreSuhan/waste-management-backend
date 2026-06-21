package com.cleanbharat.wastemanagement.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReportRequest {

    private String title; // report title

    private String description; // report details

    private String location; // area name

    private String imageUrl; // image path/url
}