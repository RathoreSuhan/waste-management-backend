package com.cleanbharat.wastemanagement.enums;

public enum AssignmentStatus {

    // Created automatically when report is created
    PENDING,

    // Cleaner has claimed the task
    CLAIMED,

    // Cleaner started cleaning
    IN_PROGRESS,

    // Cleaning finished successfully
    COMPLETED
}