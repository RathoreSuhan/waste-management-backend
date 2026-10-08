package com.cleanbharat.wastemanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * The credential the browser collected from Google, on its way to be verified.
 *
 * One field on purpose. Everything the application needs to know about the
 * visitor - who they are, what address they own, whether Google has verified it -
 * is read out of the signed token by GoogleIdentityService. Anything else sent
 * alongside it would be the caller's own claim about themselves, which is
 * exactly what this feature exists to stop trusting.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleAuthRequest {

    /*
      A Google ID token is a compact JWT of roughly a kilobyte. The cap is
      generous enough to leave room for a larger one and small enough that a
      multi-megabyte body is rejected by the validator rather than being parsed.
    */
    @NotBlank(message = "Google sign-in credential is required")
    @Size(max = 4096, message = "Google sign-in credential is not valid")
    private String credential;
}
