package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.exception.UnauthorizedAssignmentAccessException;
import org.springframework.stereotype.Service;

/**
 * ============================================================
 *  District-wide cleaner eligibility (the single copy of the rule)
 * ============================================================
 *
 *  A cleaner may work on eligible reports anywhere inside their registered
 *  STATE + DISTRICT pair - every supported ULB in that district - and
 *  nowhere else. The full pair is always compared: two states can name a
 *  district the same way, so a "Gaya" cleaner must never be matched by a
 *  "Gaya" report from another state. The registered ULB is deliberately
 *  ignored - belonging to one municipal body does not fence the cleaner
 *  inside it.
 *
 *  FAIL CLOSED
 *  -----------
 *  A missing (null) district on either side means the restriction cannot
 *  be proven, so the pair is ineligible. Legacy rows stay invisible here
 *  until the migration's reconciliation pass maps them, and eligibility is
 *  never authorized through a city-string fallback: a stored city is a
 *  display value (or a preserved legacy route), not a district.
 *
 *  WHO CALLS THIS
 *  --------------
 *  Every cleaner action path - listing, proposing, editing a proposal,
 *  starting work, writing the work diary and uploading completion proof -
 *  asks this one service, so the rule cannot drift between endpoints.
 *  Ownership and municipal authorization stay separate concerns; this
 *  class only answers the location question.
 * ============================================================
 */
@Service
public class CleanerDistrictEligibility {

    /** Message when either side lacks a reconciled location. */
    static final String UNRECONCILED_MESSAGE =
            "Your work area cannot be verified because the state or district is missing on this "
                    + "account or report. Cleanup work is restricted by district, so the "
                    + "location needs to be reconciled first - please contact support.";

    /** True when the cleaner may act on the report, judged on the full state+district pair. */
    public boolean isEligible(User cleaner, GarbageReport report) {

        if (cleaner.getDistrict() == null || report.getDistrict() == null
                || cleaner.getState() == null || report.getState() == null) {
            return false;
        }

        return cleaner.getState().equalsIgnoreCase(report.getState())
                && cleaner.getDistrict().equalsIgnoreCase(report.getDistrict());
    }

    /**
     * Same rule, refusing the action when it does not hold.
     *
     * Callers invoke this after ownership is proven and before the first
     * side effect, so a refusal leaves no upload, no write and no status
     * transition behind.
     */
    public void assertEligible(User cleaner, GarbageReport report) {

        if (cleaner.getDistrict() == null || report.getDistrict() == null
                || cleaner.getState() == null || report.getState() == null) {
            throw new UnauthorizedAssignmentAccessException(UNRECONCILED_MESSAGE);
        }

        if (!cleaner.getState().equalsIgnoreCase(report.getState())) {
            throw new UnauthorizedAssignmentAccessException(
                    "You can only work on cleanups within your assigned state.");
        }

        if (!cleaner.getDistrict().equalsIgnoreCase(report.getDistrict())) {
            throw new UnauthorizedAssignmentAccessException(
                    "You can only work on cleanups within your registered district.");
        }
    }
}
