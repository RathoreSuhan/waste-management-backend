package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.exception.InvalidCredentialsException;
import com.cleanbharat.wastemanagement.service.GoogleIdentityService.GoogleIdentity;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the one place that decides whether a Google credential is
 * believed.
 *
 * Everything downstream - account creation, account linking, the Clean Bharat
 * JWT, every role the token then unlocks - rests on this returning an identity
 * only when it should. A single accepted bad token is an account takeover, so
 * each way a token can be bad is pinned separately rather than covered by one
 * happy-path test.
 *
 * The cryptography itself is GoogleIdTokenVerifier's job and is not re-tested
 * here; it is mocked, and what is pinned is that this class refuses everything
 * the verifier refuses, refuses an unverified address even when the verifier is
 * satisfied, and refuses outright when nobody configured the client id.
 */
@ExtendWith(MockitoExtension.class)
class GoogleIdentityServiceTest {

    private static final String CLIENT_ID = "123456789-abcdef.apps.googleusercontent.com";
    private static final String CREDENTIAL = "google.id.token";
    private static final String SUBJECT = "117554201923456789012";
    private static final String EMAIL = "citizen@example.com";

    @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;

    private GoogleIdentityService googleIdentityService;

    @BeforeEach
    void setUp() {
        googleIdentityService = new GoogleIdentityService(googleIdTokenVerifier);

        // @Value fields are not populated without a Spring context
        ReflectionTestUtils.setField(googleIdentityService, "clientId", CLIENT_ID);
    }

    // ---------------------------------------------------------------------
    // Accepted
    // ---------------------------------------------------------------------

    @Test
    void aVerifiedTokenWithAVerifiedEmailIsAccepted() throws Exception {
        whenVerifierReturns(token(SUBJECT, EMAIL, true, "Citizen One"));

        GoogleIdentity identity = googleIdentityService.verify(CREDENTIAL);

        assertEquals(SUBJECT, identity.subject());
        assertEquals(EMAIL, identity.email());
        assertEquals("Citizen One", identity.name());
    }

    @Test
    void theAddressIsLowerCasedSoLookupsAreStable() throws Exception {
        // Google sends it lower-cased, but the account lookup must not depend
        // on that remaining true
        whenVerifierReturns(token(SUBJECT, "Citizen@Example.COM", true, "Citizen One"));

        assertEquals(EMAIL, googleIdentityService.verify(CREDENTIAL).email());
    }

    @Test
    void aMissingNameIsNotAFailure() throws Exception {
        // Google does not always send a name; it only prefills a form
        whenVerifierReturns(token(SUBJECT, EMAIL, true, null));

        assertNull(googleIdentityService.verify(CREDENTIAL).name());
    }

    // ---------------------------------------------------------------------
    // Refused
    // ---------------------------------------------------------------------

    @Test
    void anUnverifiedEmailIsRefused() throws Exception {
        /*
          The reason this feature exists. A Google account can carry an address
          whose owner has never proved they control it, and accepting one would
          be no better than the sign-up form this replaces.
        */
        whenVerifierReturns(token(SUBJECT, EMAIL, false, "Citizen One"));

        assertThrows(InvalidCredentialsException.class,
                () -> googleIdentityService.verify(CREDENTIAL));
    }

    @Test
    void anAbsentEmailVerifiedClaimIsRefused() throws Exception {
        // Absent is not the same as true, and must not be read as true
        whenVerifierReturns(token(SUBJECT, EMAIL, null, "Citizen One"));

        assertThrows(InvalidCredentialsException.class,
                () -> googleIdentityService.verify(CREDENTIAL));
    }

    @Test
    void aTokenTheVerifierRejectsIsRefused() throws Exception {
        /*
          verify() answers null for a bad signature, an issuer other than
          Google, an audience belonging to a different application, and an
          expired token alike - so this one case covers a tampered token, a
          token minted for somebody else's site, and a stale one.
        */
        whenVerifierReturns(null);

        assertThrows(InvalidCredentialsException.class,
                () -> googleIdentityService.verify(CREDENTIAL));
    }

    @Test
    void aMalformedCredentialIsRefused() throws Exception {
        when(googleIdTokenVerifier.verify(CREDENTIAL))
                .thenThrow(new IllegalArgumentException("not a JWT"));

        assertThrows(InvalidCredentialsException.class,
                () -> googleIdentityService.verify(CREDENTIAL));
    }

    @Test
    void aFailureReachingGooglesKeysIsRefusedRatherThanWavedThrough() throws Exception {
        // Unproven is unproven: an unreachable key endpoint must not become
        // an accidental "allow", which is how fail-open bugs are written
        when(googleIdTokenVerifier.verify(CREDENTIAL)).thenThrow(new IOException("keys unreachable"));

        assertThrows(InvalidCredentialsException.class,
                () -> googleIdentityService.verify(CREDENTIAL));
    }

    @Test
    void aVerificationErrorIsRefused() throws Exception {
        when(googleIdTokenVerifier.verify(CREDENTIAL))
                .thenThrow(new GeneralSecurityException("bad signature"));

        assertThrows(InvalidCredentialsException.class,
                () -> googleIdentityService.verify(CREDENTIAL));
    }

    @Test
    void aTokenWithoutASubjectIsRefused() throws Exception {
        // Valid, but there is no permanent id to key the account link on
        whenVerifierReturns(token(null, EMAIL, true, "Citizen One"));

        assertThrows(InvalidCredentialsException.class,
                () -> googleIdentityService.verify(CREDENTIAL));
    }

    @Test
    void aTokenWithoutAnEmailIsRefused() throws Exception {
        whenVerifierReturns(token(SUBJECT, null, true, "Citizen One"));

        assertThrows(InvalidCredentialsException.class,
                () -> googleIdentityService.verify(CREDENTIAL));
    }

    @Test
    void anUnconfiguredServerRefusesEveryCredential() {
        /*
          With no client id there is no audience to pin, so a token minted for
          any other Google application would otherwise verify here. Refusing
          before the verifier is even called is the fail-closed answer.
        */
        ReflectionTestUtils.setField(googleIdentityService, "clientId", "");

        assertThrows(InvalidCredentialsException.class,
                () -> googleIdentityService.verify(CREDENTIAL));
    }

    // ---------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------

    private void whenVerifierReturns(GoogleIdToken idToken) throws Exception {
        when(googleIdTokenVerifier.verify(CREDENTIAL)).thenReturn(idToken);
    }

    /** A token as it looks once the verifier has already accepted it. */
    private GoogleIdToken token(String subject, String email, Boolean emailVerified, String name) {

        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();

        payload.setSubject(subject);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);

        if (name != null) {
            payload.set("name", name);
        }

        // The signature is not re-checked here; the verifier is mocked above
        return new GoogleIdToken(
                new JsonWebSignature.Header(),
                payload,
                new byte[0],
                new byte[0]
        );
    }
}
