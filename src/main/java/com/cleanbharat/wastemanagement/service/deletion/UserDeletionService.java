package com.cleanbharat.wastemanagement.service.deletion;

import com.cleanbharat.wastemanagement.entity.User;

/**
 * Handles deletion of users together with all resources owned by them.
 */
public interface UserDeletionService {

    /**
     * Deletes a citizen.
     */
    void deleteCitizen(User citizen);

    /**
     * Deletes a cleaner.
     */
    void deleteCleaner(User cleaner);
}