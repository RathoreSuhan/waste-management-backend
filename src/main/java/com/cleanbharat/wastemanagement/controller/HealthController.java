package com.cleanbharat.wastemanagement.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ============================================================================
 * Health Controller
 * ============================================================================
 *
 * A single public endpoint whose only job is to answer as early and as cheaply
 * as possible: GET /api/health -> {"status":"UP"}.
 *
 * Why it exists
 * -------------
 * The service is hosted on a free plan that stops the container after a spell
 * with no traffic. The next request has to start the container and build the
 * whole Spring context before it can be served, which takes close to a minute.
 * Left alone, that wait lands on whoever presses Sign In first.
 *
 * The frontend therefore calls this endpoint the moment the site is opened, so
 * the container is already starting while the visitor is still reading the page
 * or filling in the form. By the time they submit, the server is usually up.
 *
 * Why it is deliberately empty
 * ----------------------------
 * No injected dependencies, no repository, no database call. Spring cannot
 * serve any request until the context is ready, so a reply from here means
 * exactly one thing - the application is up - and it means it without adding
 * work of its own.
 *
 * /actuator/health is not used for this. Its default database indicator issues
 * a query, which is both slower and able to report DOWN while the managed
 * database is itself resuming, and exposing actuator publicly would widen the
 * surface for no gain.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    // Built once at class load: the response body needs no work per request
    private static final Map<String, String> UP = Map.of("status", "UP");

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {

        return ResponseEntity
                .ok()

                /*
                  no-store, because a cached reply defeats the whole point.

                  A browser or proxy that answers this from its own cache never
                  sends the request on to the host, so the container is never
                  woken - the one thing the call is for.
                */
                .cacheControl(CacheControl.noStore())

                .body(UP);
    }
}
