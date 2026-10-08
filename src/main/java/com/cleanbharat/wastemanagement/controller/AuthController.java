package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.GoogleAuthRequest;
import com.cleanbharat.wastemanagement.dto.GoogleAuthResponse;
import com.cleanbharat.wastemanagement.dto.GoogleRegisterRequest;
import com.cleanbharat.wastemanagement.dto.RegisterRequest;
import com.cleanbharat.wastemanagement.dto.RegisterResponse;
import com.cleanbharat.wastemanagement.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.cleanbharat.wastemanagement.dto.LoginRequest;
import com.cleanbharat.wastemanagement.dto.AuthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Sign-in and sign-up.
 *
 * Every path here is reachable without a token - SecurityConfig lists
 * /api/auth/** under permitAll, and JwtAuthenticationFilter skips it, because a
 * visitor signing in has no token yet. That does not make it unguarded:
 * RateLimitProperties puts the whole prefix in the "auth" tier, so these are the
 * most tightly throttled endpoints in the application, keyed by IP since there
 * is no principal to key on.
 *
 * Two ways to create an account, differing only in how the address is proved:
 * an emailed code (POST /register) or a Google identity (POST /google/register).
 * Neither takes an address on trust.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    /**
     * Signs up with a password, in two calls to this same endpoint.
     *
     * Without a verificationCode it emails a code and answers
     * verificationRequired. With one it creates the account and answers a JWT.
     * One endpoint rather than two because the body is the same either way, and
     * the form has to be re-validated at creation time regardless.
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Signs in with a Google credential.
     *
     * Answers either a Clean Bharat JWT, or registrationRequired for a Google
     * account with no Clean Bharat account yet - which is not an error, so it
     * is a 200 rather than a 404.
     */
    @PostMapping("/google")
    public ResponseEntity<GoogleAuthResponse> googleSignIn(@Valid @RequestBody GoogleAuthRequest request) {
        GoogleAuthResponse response = authService.googleSignIn(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Completes a first-time Google sign-up with the details Google cannot
     * supply - role and location - and signs the new account in.
     */
    @PostMapping("/google/register")
    public ResponseEntity<GoogleAuthResponse> googleRegister(@Valid @RequestBody GoogleRegisterRequest request) {
        GoogleAuthResponse response = authService.googleRegister(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Password sign-in.
     *
     * Serves accounts created through POST /register, municipal corporations -
     * whose password is issued by an administrator - and accounts that predate
     * Google sign-in.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
