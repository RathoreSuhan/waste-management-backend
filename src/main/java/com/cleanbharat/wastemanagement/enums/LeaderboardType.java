package com.cleanbharat.wastemanagement.enums;

/**
 * Represents the scope of
 * the leaderboard.
 */
public enum LeaderboardType {

    // Top cleaners across India
    NATIONAL,

    // Top cleaners of a state
    STATE,

    // Top cleaners of a district, across every ULB inside it. Added by the
    // location refactor alongside CITY: the city scope stays supported until
    // the documented cleanup confirms no client depends on it.
    DISTRICT,

    // Top cleaners of a city
    CITY
}