package com.cleanbharat.wastemanagement.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter // getters
@AllArgsConstructor // constructor
public class ErrorResponse {                //ErrorResponse DTO

    private String message; // error message

    private int status; // http status
}