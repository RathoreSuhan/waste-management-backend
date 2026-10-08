package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.exception.InvalidCredentialsException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * ============================================================
 *  Google identity verification
 * ============================================================
 *
 *  Turns the opaque credential the browser collected from Google into an
 *  identity this application is willing to act on - or refuses it.
 *
 *  THE RULE THIS CLASS EXISTS TO ENFORCE
 *  -------------------------------------
 *  Nothing the frontend sends about who the visitor is may be believed. The
 *  credential is the only thing accepted, and only because Google signed it;
 *  the email, name and account id are read out of that signed token, never
 *  from the request body. A caller who posts {"email": "admin@..."} gets
 *  nowhere, because no such field is read anywhere in this flow.
 *
 *  WHAT IS CHECKED
 *  ---------------
 *  verify() covers the cryptography and the token's own validity:
 *    signature   against Google's current published keys
 *    issuer      accounts.google.com only
 *    audience    this project's Google client id (see GoogleAuthConfig)
 *    expiry      against the system clock
 *
 *  Three further checks are made here, because a token can be perfectly valid
 *  and still not be an identity worth creating an account from:
 *    sub present            the permanent account id, which the link is keyed on
 *    email present          the application has no other way to address a person
 *    email_verified == true the whole point of the exercise
 *
 *  That last one is the reason for this feature. A Google account can carry an
 *  address its owner has never proved they control, and accepting it would be
 *  no better than the sign-up form it replaces.
 *
 *  WHAT IS NOT KEPT
 *  ----------------
 *  The credential is read and discarded. It is never stored, never logged, and
 *  never written to the users table - it is a live bearer token until it
 *  expires, and this application has no use for it after this method returns.
 * ============================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleIdentityService {

    /*
      Messages are written for the person reading them on the sign-in page, and
      deliberately say nothing about which internal check failed. A forged
      token, a token minted for another application and an expired one all
      answer the same way, so probing this endpoint reveals nothing about the
      verification; the distinguishing detail goes to the server log instead.
    */
    private static final String NOT_CONFIGURED =
            "Google sign-in is not available on this server right now. Please sign in with your email and password, or try again later.";

    private static final String REJECTED =
            "Your Google sign-in could not be verified. Please try signing in with Google again.";

    private static final String EMAIL_NOT_VERIFIED =
            "The email address on your Google account has not been verified. Verify it with Google, then sign in again.";

    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    // Read only to tell "nobody configured this" apart from "the token was bad"
    @Value("${google.client-id:}")
    private String clientId;

    /**
     * One verified Google account.
     *
     * @param subject Google's permanent id for the account (the 'sub' claim)
     * @param email   verified address, lower-cased so lookups are stable
     * @param name    display name Google holds, or null if it sent none
     */
    public record GoogleIdentity(String subject, String email, String name) {
    }

    /**
     * Verifies a Google credential and returns the account behind it.
     *
     * @throws InvalidCredentialsException if the credential is not a valid
     *         Google ID token for this application, or the account's email
     *         address is not verified
     */
    public GoogleIdentity verify(String credential) {

        if (!StringUtils.hasText(clientId)) {
            // Fails closed: with no audience to pin, no token can be trusted
            log.error("GOOGLE_CLIENT_ID is not set, so Google sign-in cannot be verified.");

            throw new InvalidCredentialsException(NOT_CONFIGURED);
        }

        GoogleIdToken idToken;

        try {
            idToken = googleIdTokenVerifier.verify(credential);
        } catch (Exception ex) {
            /*
              A malformed credential, or Google's key endpoint being
              unreachable. Either way the identity is unproven, so the request
              is refused rather than let through.
            */
            log.warn("Could not verify a Google credential: {}", ex.getClass().getSimpleName());

            throw new InvalidCredentialsException(REJECTED);
        }

        // null means a failed check: signature, issuer, audience or expiry
        if (idToken == null) {
            log.debug("A Google credential failed verification.");

            throw new InvalidCredentialsException(REJECTED);
        }

        GoogleIdToken.Payload payload = idToken.getPayload();

        String subject = payload.getSubject();

        String email = payload.getEmail();

        // A token without these is valid but useless: there is nobody to be
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(email)) {
            log.warn("A verified Google token arrived without a subject or an email claim.");

            throw new InvalidCredentialsException(REJECTED);
        }

        // Boolean.TRUE.equals, because the claim may be absent entirely
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new InvalidCredentialsException(EMAIL_NOT_VERIFIED);
        }

        return new GoogleIdentity(
                subject,

                // Google sends this lower-cased already; normalised so the
                // account lookup cannot depend on that staying true
                email.toLowerCase(Locale.ROOT),

                readName(payload)
        );
    }

    /** The display name, if Google sent one. An untyped claim, so read defensively. */
    private String readName(GoogleIdToken.Payload payload) {

        Object name = payload.get("name");

        return name instanceof String text && StringUtils.hasText(text) ? text.trim() : null;
    }
}
