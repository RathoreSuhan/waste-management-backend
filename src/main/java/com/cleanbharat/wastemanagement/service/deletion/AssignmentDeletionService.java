package com.cleanbharat.wastemanagement.service.deletion;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;

/**
 * Handles deletion of a CleanupAssignment and every resource owned by it.
 */
public interface AssignmentDeletionService {

    /**
     * Deletes one cleanup assignment together with all dependent data.
     */
    void deleteAssignment(CleanupAssignment assignment);

}