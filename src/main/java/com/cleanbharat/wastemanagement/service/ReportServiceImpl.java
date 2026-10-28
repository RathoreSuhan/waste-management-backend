package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.MyReportSummaryResponse;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.exception.InvalidReportCreationException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.mapper.ReportMapper;
import com.cleanbharat.wastemanagement.util.LocationUtil;
import com.cleanbharat.wastemanagement.util.PaginationUtil;
import com.cleanbharat.wastemanagement.dto.ai.AIReportValidationResponse;
import com.cleanbharat.wastemanagement.service.ai.AIReportValidationService;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService.ResolvedLocation;
import com.cleanbharat.wastemanagement.service.location.ReportDuplicateValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor // constructor injection
public class ReportServiceImpl implements ReportService {

    /*
      Properties a caller is allowed to order the report register by.

      A whitelist rather than a free-form field name: the value arrives as a
      request parameter and ends up in an ORDER BY clause, so it may only
      ever be one of these literal strings. "engagementScore" is here
      because the trending register ranks by it; "createdAt" is the
      register's default.
    */
    private static final Set<String> REPORT_SORT_PROPERTIES =
            Set.of("createdAt", "engagementScore");

    private final GarbageReportRepository reportRepository; // report repo
    private final UserRepository userRepository; // user repo
    private final MunicipalCorporationRepository municipalCorporationRepository; // resolves a report's destination
    private final IndianLocationService indianLocationService; // validates the directory triple on code-backed reports
    private final CloudinaryService cloudinaryService; // cloudinary service for image upload
    private final CleanupAssignmentService cleanupAssignmentService; // service to create cleanup assignment
    private final ReportMapper reportMapper; // Shared mapper for Report -> DTO conversion
    private final AIReportValidationService aiReportValidationService; // AI validation
    private final ReportDuplicateValidationService reportDuplicateValidationService; // Duplicate validation


    /*
     * @CacheEvict(value = "homepage_impact_stats", allEntries = true)
     *
     * WHAT THIS DOES:
     *   AFTER createReport() finishes successfully, Spring evicts
     *   ALL entries from the 'homepage_impact_stats' cache.
     *
     * WHY ALL ENTRIES = true (not a single key):
     *   When a citizen creates a report, the total report count
     *   increments. Since there's only ONE key ("platform-impact") in this
     *   cache, allEntries = true is the same as evicting that key.
     *   Using allEntries = true keeps the code flexible if we
     *   ever add more keys to this cache later.
     *
     * WHEN DOES THIS RUN?
     *   After the method returns successfully (post-execution).
     *   If createReport() throws an exception → eviction is NOT
     *   triggered (the cache still holds the old, correct data).
     *
     * WHY EVICT HERE:
     *   createReport() adds a new GarbageReport with status PENDING.
     *   The next call to getPlatformImpact() must reflect the
     *   new reportsFiled count. Without eviction, the cached
     *   "reportsFiled = 42" would still show 42 until TTL expiry.
     *
     * TTL VS EVICTION TRADE-OFF:
     *   TTL alone = stale data for up to 10 minutes.
     *   Eviction = instant consistency.
     *   Best practice: use BOTH (eviction for correctness + TTL
     *   as a safety net for edge cases where eviction is missed).
     *
     * THE TWO DASHBOARD CACHES GO WITH IT:
     *   A new report raises the admin "Total Reports" and "Pending"
     *   tiles, and createDefaultAssignment() routes the site to a city
     *   body, raising that corporation's "Relevant Reports". Both
     *   overviews would otherwise keep showing the old figure.
     * ============================================================
     */
    @Caching(evict = {
            @CacheEvict(value = "homepage_impact_stats", allEntries = true),
            @CacheEvict(value = "admin_dashboard_stats", allEntries = true),
            @CacheEvict(value = "municipal_dashboard_stats", allEntries = true)
    })
    @Transactional
    @Override
    public ReportResponse createReport(CreateReportRequest request, MultipartFile image) {

        // Logged-in user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Only citizens can create reports
        if (user.getRole() != Role.ROLE_CITIZEN) {
            throw new InvalidReportCreationException(
                    "Only citizens can create garbage reports."
            );
        }

        /*
         * Step 1
         * Clean up the submitted location text and work out where the report
         * is and where it must go.
         *
         * Code-backed submissions (the directory cascade) carry the three LGD
         * codes: they are validated against the directory and their municipal
         * destination is resolved HERE - before AI validation, the duplicate
         * check and the Cloudinary upload - so a ULB nobody has onboarded is
         * refused cheaply, with an actionable message and no partial report.
         * Legacy submissions carry free-text city/state and keep today's
         * behaviour: their destination is resolved when the default assignment
         * is created, after the upload.
         */
        normalizeLocationFields(request);

        ResolvedLocation location = resolveReportLocation(request);

        MunicipalCorporation destination = null;

        if (location != null) {
            destination = municipalCorporationRepository.findByUlbCode(location.ulbCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "The Municipal Corporation for \"" + location.city()
                                    + "\" (ULB " + location.ulbCode()
                                    + ") is not onboarded yet, so this report cannot be filed. "
                                    + "Your report was not saved - please choose another municipal "
                                    + "area or check back later."));
        }

        /*
         * Step 2
         * AI validates uploaded image.
         */
        AIReportValidationResponse aiResponse = aiReportValidationService.validateReportImage(image);

        /*
         * Step 3
         * Prevent duplicate reports.
         */
        reportDuplicateValidationService.validateNoDuplicateReport(request);


        String imageUrl = null;
        try {
            /*
             * Step 4
             * Upload image only after all validations pass.
             */
            imageUrl = cloudinaryService.uploadFile(image);

            /*
             * Step 5
             * Create report entity. Code-backed submissions store the
             * directory's canonical names plus the district and ULB code;
             * legacy submissions store the free text exactly as before.
             */
            GarbageReport report = GarbageReport.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .address(request.getAddress())
                    .landmark(request.getLandmark())
                    .city(location != null ? location.city() : request.getCity())
                    .state(location != null ? location.state() : request.getState())
                    .district(location != null ? location.district() : null)
                    .ulbCode(location != null ? location.ulbCode() : null)
                    .pincode(request.getPincode())

                    // AI detected garbage category
                    .garbageCategory(aiResponse.getGarbageCategory())

                    .imageUrl(imageUrl)
                    .status(ReportStatus.PENDING)
                    .user(user)
                    .build();

            /*
             * Step 6
             * Save report.
             */
            GarbageReport savedReport = reportRepository.save(report);
            log.info("Creating cleanup assignment for report {}", savedReport.getId());

            /*
             * Step 7
             * Automatically create cleanup assignment, handing in the
             * destination already resolved for code-backed reports (null on
             * the legacy path, which routes by stored city as always).
             */
            cleanupAssignmentService.createDefaultAssignment(savedReport, destination);
            log.info("Cleanup assignment created successfully.");

            return reportMapper.toResponse(savedReport);
        }catch (Exception ex) {

            // Database transaction will roll back automatically
            // Remove uploaded image from Cloudinary
            if (imageUrl != null) {
                cloudinaryService.deleteFile(imageUrl);
            }
            throw ex;
        }
    }

    /**
     * Works out where the report is, from one of the two accepted shapes.
     *
     * Code-backed: the three LGD codes must arrive together and describe one
     * real directory place (invalid combinations are refused here, before any
     * expensive work). Legacy: free-text city and state, exactly as the old
     * form sent them.
     *
     * @return the validated directory location, or null on the legacy path
     */
    private ResolvedLocation resolveReportLocation(CreateReportRequest request) {

        boolean hasCodes = hasText(request.getStateCode())
                || hasText(request.getDistrictCode())
                || hasText(request.getUlbCode());

        if (!hasCodes) {

            if (!hasText(request.getCity()) || !hasText(request.getState())) {
                throw new InvalidReportCreationException(
                        "City and state are required when no location codes are sent.");
            }

            return null; // Legacy free-text report: no district, no ULB code
        }

        if (!hasText(request.getStateCode()) || !hasText(request.getDistrictCode())
                || !hasText(request.getUlbCode())) {
            throw new InvalidReportCreationException(
                    "State, district and city/municipal-area codes must be sent together.");
        }

        // Answers 400 for any combination the directory does not list
        return indianLocationService.resolve(
                request.getStateCode(),
                request.getDistrictCode(),
                request.getUlbCode());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Brings the submitted location text to a consistent shape.
     *
     * City and state are title-cased the same way they are for users at
     * registration, so "kolkata", "Kolkata" and "KOLKATA" all become the one
     * value. Reports are grouped and matched by city, so without this the
     * same place would split into several distinct entries. On code-backed
     * submissions these fields are absent (the directory supplies the names),
     * so there is nothing to normalise.
     *
     * Only whitespace is stripped from the address, landmark and pincode,
     * since those are free text rather than grouping keys.
     *
     * Blank fields are already rejected before this runs where they are
     * required, so city and state are safe to normalize here.
     */
    private void normalizeLocationFields(CreateReportRequest request) {

        if (hasText(request.getCity())) {
            request.setCity(LocationUtil.normalizeLocation(request.getCity()));
        }
        if (hasText(request.getState())) {
            request.setState(LocationUtil.normalizeLocation(request.getState()));
        }

        request.setAddress(request.getAddress().trim());
        request.setPincode(request.getPincode().trim());

        // Landmark is optional, so an omitted or empty value is stored as null
        String landmark = request.getLandmark();

        if (landmark == null || landmark.isBlank()) {
            request.setLandmark(null);
        } else {
            request.setLandmark(landmark.trim());
        }
    }

    @Override
    public PageResponse<ReportResponse> getAllReports(
            int page,
            int size,
            String sortBy,
            String direction,
            String keyword,
            ReportStatus status
    ) {

        // Newest first unless the caller asks for a whitelisted alternative
        Sort sort = PaginationUtil.resolveSort(
                sortBy,
                direction,
                REPORT_SORT_PROPERTIES,
                "createdAt"
        );

        Pageable pageable = PaginationUtil.resolve(page, size, sort);

        /*
          Two filters, one query each.

          A keyword searches across four fields at once, which is a
          different WHERE clause from a status filter, and both differ from
          the unfiltered register. Rather than one query with every
          combination and a row of null checks, the register picks the
          query that matches what was asked for. A keyword takes precedence
          when both are present, because the search already spans every
          status.
        */
        Page<GarbageReport> reports;

        if (keyword != null && !keyword.isBlank()) {

            reports = reportRepository.searchReportsPaged(
                    keyword.trim(),
                    pageable
            );

        } else if (status != null) {

            reports = reportRepository.filterReportsPaged(
                    status,
                    null,
                    null,
                    pageable
            );

        } else {

            // No filters - the whole register, one page at a time.
            // findAllBy rather than findAll so the reporter is joined in
            // and a page does not cost an extra query per row.
            reports = reportRepository.findAllBy(pageable);
        }

        return PageResponse.from(reports, reportMapper::toResponse);
    }

    @Override
    public ReportResponse getReport(Long id) {
        GarbageReport report =
                reportRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        return reportMapper.toResponse(report);
    }

    @Override
    public PageResponse<ReportResponse> getMyReports(
            int page,
            int size,
            ReportStatus status
    ) {

        User user = currentUser();

        /*
          Newest first, and the id breaks the tie.

          Two reports filed in the same second would otherwise have no
          defined order, and a row could then appear on two pages while
          another was skipped entirely.
        */
        Pageable pageable = PaginationUtil.resolve(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Page<GarbageReport> reports = status == null
                ? reportRepository.findByUser(user, pageable)
                : reportRepository.findByUserAndStatus(user, status, pageable);

        return PageResponse.from(reports, reportMapper::toResponse);
    }

    @Override
    public MyReportSummaryResponse getMyReportsSummary() {

        User user = currentUser();

        /*
          One grouped query, read into one number per status.

          Three counts could be three queries, but the database can group
          them into a single pass. A status the citizen has never used is
          absent from the result rather than present as zero, so every
          lookup defaults to zero.
        */
        long total = 0;
        long pending = 0;
        long inProgress = 0;
        long resolved = 0;

        for (GarbageReportRepository.StatusCount row
                : reportRepository.countByUserGroupedByStatus(user)) {

            total += row.getTotal();

            switch (row.getStatus()) {
                case PENDING -> pending = row.getTotal();
                case IN_PROGRESS -> inProgress = row.getTotal();
                case RESOLVED -> resolved = row.getTotal();
            }
        }

        return MyReportSummaryResponse.builder()
                .total(total)
                .pending(pending)
                .inProgress(inProgress)
                .resolved(resolved)
                .build();
    }

    /**
     * The signed-in citizen behind the current request.
     *
     * Both /my endpoints resolve the same user the same way - from the
     * token, never from a parameter - so the lookup lives here rather than
     * being repeated and risking one copy drifting.
     */
    private User currentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication(); // logged user

        String email = authentication.getName(); // email

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}