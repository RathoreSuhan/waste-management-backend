package com.cleanbharat.wastemanagement.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * ============================================================
 *  Google Sign-In configuration
 * ============================================================
 *
 *  Builds the one object that decides whether a credential presented by the
 *  browser really was issued by Google for this application.
 *
 *  WHY A LIBRARY AND NOT A HAND-WRITTEN CHECK
 *  ------------------------------------------
 *  A Google ID token is signed with a key Google rotates, published as a JWKS
 *  document. Verifying it by hand would mean fetching that document, caching it
 *  for exactly as long as Google says, re-fetching when an unknown key id turns
 *  up, and getting the issuer and audience comparisons right - roughly seventy
 *  lines of security-critical code whose failure mode is silently accepting a
 *  forged token. GoogleIdTokenVerifier already does all of it.
 *
 *  WHAT setAudience BUYS
 *  ---------------------
 *  Without it, a token minted for ANY Google application would verify here: the
 *  signature is Google's either way. Pinning the audience to this project's own
 *  client id is what stops a token obtained by an unrelated site from being
 *  replayed against this API.
 *
 *  THE EMPTY DEFAULT
 *  -----------------
 *  "${google.client-id:}" resolves from the GOOGLE_CLIENT_ID environment
 *  variable, so no configuration file has to be edited to deploy this. Left
 *  unset the audience is the empty string, which no real token can match, so an
 *  unconfigured deployment refuses every Google sign-in rather than accepting
 *  tokens it cannot vouch for. GoogleIdentityService reads the same value and
 *  turns that case into a message naming the missing configuration, because
 *  "rejected" is a poor way to learn an environment variable was forgotten.
 * ============================================================
 */
@Configuration
public class GoogleAuthConfig {

    // Public identifier of this project's Google OAuth client. Not a secret:
    // the frontend ships it too. There is no client secret in this flow.
    @Value("${google.client-id:}")
    private String clientId;

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier() {

        /*
          Issuer is not set here on purpose: the builder already accepts only
          Google's own issuers ("accounts.google.com" and
          "https://accounts.google.com"), and expiry is checked against the
          system clock on every verify() call.
        */
        return new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(List.of(clientId))
                .build();
    }
}
