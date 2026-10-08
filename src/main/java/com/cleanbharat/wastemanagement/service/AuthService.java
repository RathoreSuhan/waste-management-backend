package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.GoogleAuthRequest;
import com.cleanbharat.wastemanagement.dto.GoogleAuthResponse;
import com.cleanbharat.wastemanagement.dto.GoogleRegisterRequest;
import com.cleanbharat.wastemanagement.dto.RegisterRequest;
import com.cleanbharat.wastemanagement.dto.RegisterResponse;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.CleanerType;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.*;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.GoogleIdentityService.GoogleIdentity;
import com.cleanbharat.wastemanagement.util.LocationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.cleanbharat.wastemanagement.dto.AuthResponse;
import com.cleanbharat.wastemanagement.dto.LoginRequest;
import com.cleanbharat.wastemanagement.security.JwtService;
import org.springframework.cache.annotation.CacheEvict;

import java.util.Optional;
import java.util.UUID;

/**
 * ============================================================
 *  Sign-up and sign-in
 * ============================================================
 *
 *  HOW AN ACCOUNT COMES INTO EXISTENCE
 *  -----------------------------------
 *  Only with an email address somebody has proved they can read, by one of two
 *  routes that differ in nothing but the proof:
 *
 *    Google      the address comes from a token Google signed, carrying
 *                email_verified - see GoogleIdentityService
 *    Email code  a six-digit code is delivered to the address and typed back -
 *                see EmailVerificationService
 *
 *  Both end in createAccount below, so there is one definition of what creating
 *  an account means, and both reject exactly the same things. The second route
 *  exists because Google cannot vouch for a Yahoo, Outlook, college or workplace
 *  address, and refusing those people an account was never the intent.
 *
 *  The unverified sign-up this replaced simply took an address on trust.
 *
 *  HOW AN ACCOUNT IS SIGNED IN
 *  ---------------------------
 *  Three ways, and all three end in the same Clean Bharat JWT:
 *
 *    Google, already linked   google_subject matches a users row
 *    Google, first time       verified email matches a users row, which is
 *                             linked once and then behaves as above
 *    Password                 the password chosen at sign-up, or issued by an
 *                             administrator to a municipal corporation
 *
 *  WHY THE FIRST-TIME GOOGLE LINK IS SAFE
 *  --------------------------------------
 *  Linking on a matching verified address would be dangerous if an account could
 *  be registered to an address its owner had never seen - whoever got there
 *  first would be handed the real owner's account the moment they signed in with
 *  Google. Every route above proves the address first, which is what closes it.
 * ============================================================
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final MunicipalCorporationRepository municipalCorporationRepository; // municipal bodies live in their own table
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleIdentityService googleIdentityService; // the only thing that trusts Google
    private final EmailVerificationService emailVerificationService; // proves an address for every other provider

    /**
     * Signs up with a password, in two calls.
     *
     * Without a verification code this validates the whole form and emails a
     * code, creating nothing. With one, it validates again, checks the code and
     * creates the account.
     *
     * Validating before sending matters: a request that was going to be refused
     * anyway should not cost an email. Validating again afterwards matters more:
     * the first call's "this address is free" answer is minutes old by then, and
     * must not be the thing an account is created on.
     *
     * The cache eviction covers the second call, which writes a row. It also
     * fires on the first, where there is nothing to evict - a recomputed
     * dashboard figure now and then, rather than splitting this in two to
     * satisfy an annotation that can only sit on a whole method.
     */
    @CacheEvict(value = "admin_dashboard_stats", allEntries = true)
    public RegisterResponse register(RegisterRequest request) {

        String email = request.getEmail().trim();

        rejectUnavailableAccount(email, request.getRole(), request.getCleanerType());

        // First call: ask the person to prove the address is theirs
        if (!StringUtils.hasText(request.getVerificationCode())) {

            emailVerificationService.sendCode(email);

            return RegisterResponse.builder()
                    .verificationRequired(true)
                    .email(email)
                    .build();
        }

        // Second call: throws unless the code is the one that was sent
        emailVerificationService.verifyCode(email, request.getVerificationCode());

        User user = createAccount(
                request.getName(),
                email,
                passwordEncoder.encode(request.getPassword()),
                null, // no Google account linked yet; signing in with Google later links it
                request.getRole(),
                request.getCleanerType(),
                request.getOrganizationName(),
                request.getState(),
                request.getCity()
        );

        return RegisterResponse.builder()
                .verificationRequired(false)
                .token(jwtService.generateToken(user.getEmail()))
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    /**
     * Signs in with Google, linking or reporting that an account is still needed.
     *
     * Nothing is created here. A Google account with no Clean Bharat account
     * behind it is answered with registrationRequired, because Google cannot
     * supply the role and location this application requires - see
     * googleRegister.
     */
    public GoogleAuthResponse googleSignIn(GoogleAuthRequest request) {


        // Refused outright unless Google signed it and the email is verified
        GoogleIdentity identity = googleIdentityService.verify(request.getCredential());

        /*
          Already linked. Asked first and answered from the subject alone,
          because 'sub' is Google's permanent id for the account: someone who
          has since changed the address on their Google account is still
          recognised, and still signs in to the same Clean Bharat account.
        */
        Optional<User> linked = userRepository.findByGoogleSubject(identity.subject());

        if (linked.isPresent()) {
            return signedIn(linked.get());
        }

        /*
          A city's official inbox is not a personal account. The corporation
          signs in through its own row with the password the administrator
          issued, and there is no google_subject on that table to link to, so
          this is refused with wording that points at the right door rather
          than silently creating a second, powerless account for the address.
        */
        if (municipalCorporationRepository.existsByEmailIgnoreCase(identity.email())) {
            throw new InvalidCredentialsException(
                    "This address belongs to a municipal body. Sign in with the official credentials issued by the administrator.");
        }

        /*
          An account that already exists for this verified address, from before
          Google sign-in. Linked here rather than duplicated: the person has
          proved to Google that they own the mailbox the account was registered
          with, and creating a second account would split their reports, points
          and history across two identities.

          Case-insensitive, because Google reports a lower-cased address while
          the old sign-up form stored whatever was typed.
        */
        Optional<User> existing = userRepository.findByEmailIgnoreCase(identity.email());

        if (existing.isPresent()) {
            User user = existing.get();

            user.setGoogleSubject(identity.subject()); // linked once; the branch above answers from now on

            userRepository.save(user);

            return signedIn(user);
        }

        // Nobody yet. The browser comes back with the same credential plus a profile.
        return GoogleAuthResponse.builder()
                .registrationRequired(true)
                .email(identity.email())
                .name(identity.name())
                .build();
    }

    /**
     * Creates an account for a verified Google identity and signs it in.
     */
    @CacheEvict(value = "admin_dashboard_stats", allEntries = true)
    public GoogleAuthResponse googleRegister(GoogleRegisterRequest request) {

        /*
          Verified again, not carried over from the sign-in call. That call
          issued nothing and promised nothing; without this check anyone could
          create an account for any address simply by posting this request.
        */
        GoogleIdentity identity = googleIdentityService.verify(request.getCredential());

        rejectUnavailableAccount(identity.email(), request.getRole(), request.getCleanerType());

        /*
          Also by subject, which the shared check above cannot cover: this
          credential is valid for an hour, so a double-submitted form - or a
          second browser tab - would otherwise reach here twice and try to
          create the account twice.
        */
        if (userRepository.findByGoogleSubject(identity.subject()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        User user = createAccount(
                request.getName(),

                // From the signed token, never from the request body
                identity.email(),

                /*
                  A Google account has no password, but the column is NOT NULL
                  and CustomUserDetailsService expects a hash. A hash of a fresh
                  random value satisfies both and can never be matched by any
                  password a caller could send, so POST /api/auth/login answers
                  these accounts with its usual "Invalid email or password".
                */
                passwordEncoder.encode(UUID.randomUUID().toString()),

                identity.subject(),
                request.getRole(),
                request.getCleanerType(),
                request.getOrganizationName(),
                request.getState(),
                request.getCity()
        );

        return signedIn(user);
    }

    /**
     * Everything that disqualifies a sign-up, whichever route it arrived by.
     *
     * Shared so the two routes cannot drift apart: a rule added to one and
     * forgotten on the other is a way in, and "Municipal registration is not
     * allowed" is exactly the kind of rule that must hold on every path.
     */
    private void rejectUnavailableAccount(String email, Role role, CleanerType cleanerType) {

        // Prevent self-registration as ADMIN
        if (role == Role.ROLE_ADMIN) {
            throw new UnauthorizedRegistrationException("Admin registration is not allowed.");
        }

        // Municipal dashboard access is not something a user can sign up for.
        // Only the email the admin saved for that city can open the Municipal Console.
        if (role == Role.ROLE_MUNICIPAL_OFFICER) {
            throw new UnauthorizedRegistrationException(
                    "Municipal registration is not allowed. Municipal bodies sign in with the official email registered by the admin.");
        }

        // The official email of a city's corporation can never become a normal account
        if (municipalCorporationRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        /*
          Case-insensitive, because Google reports a lower-cased address while a
          row created through the sign-up form keeps whatever was typed. An exact
          match would miss "Suhan@Gmail.com" and let a second account through for
          the same person.
        */
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        // Validation for cleaners
        if (role == Role.ROLE_CLEANER && cleanerType == null) {
            throw new InvalidRegistrationException("Cleaner type is required for ROLE_CLEANER.");
        }
    }

    /**
     * Writes the account row.
     *
     * The single place a User is created, reached only after the caller has
     * proved the address and rejectUnavailableAccount has passed.
     *
     * @param googleSubject Google's permanent account id, or null for an account
     *                      that proved its address with an emailed code
     */
    private User createAccount(
            String name,
            String email,
            String passwordHash,
            String googleSubject,
            Role role,
            CleanerType cleanerType,
            String organizationName,
            String state,
            String city
    ) {

        User user = User.builder()
                .name(name)
                .email(email)
                .googleSubject(googleSubject)
                .password(passwordHash)
                .role(role)
                .cleanerType(cleanerType)
                .organizationName(organizationName)
                .state(LocationUtil.normalizeLocation(state))
                .city(LocationUtil.normalizeLocation(city))
                .rewardPoints(0)
                .build();

        return userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        // A city's municipal body signs in with the official email the admin registered,
        // so that inbox is checked before the normal user table
        Optional<MunicipalCorporation> corporation =
                municipalCorporationRepository.findByEmailIgnoreCase(request.getEmail());
        if (corporation.isPresent()) {
            return loginAsMunicipalCorporation(corporation.get(), request.getPassword());
        }

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        // Verify password
        boolean isPasswordValid =
                passwordEncoder.matches(
                        request.getPassword(),
                        user.getPassword()
                );

        if (!isPasswordValid) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Generate JWT Token
        String token = jwtService.generateToken(user.getEmail());

        // Return response
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    /**
     * Signs in a municipal corporation using the row the admin created for that city.
     * The corporation itself is the login identity, which is what keeps one city's
     * dashboard tied to exactly one official email.
     */
    private AuthResponse loginAsMunicipalCorporation(MunicipalCorporation corporation, String rawPassword) {
        // A corporation added before this feature has its password back-filled on startup,
        // so a blank hash means the account is simply not usable yet
        boolean isPasswordValid = corporation.getPassword() != null
                && passwordEncoder.matches(rawPassword, corporation.getPassword());

        if (!isPasswordValid) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(corporation.getEmail());

        return AuthResponse.builder()
                .token(token)
                .email(corporation.getEmail())
                .role(Role.ROLE_MUNICIPAL_OFFICER) // role is implied by being a registered corporation
                .build();
    }

    /**
     * The end of every Google path: this application's own JWT for the account.
     *
     * Deliberately the same token, with the same subject and expiry, that
     * password sign-in issues - so JwtAuthenticationFilter, the role rules in
     * SecurityConfig and every protected endpoint behave identically however
     * somebody signed in. Google's token plays no further part.
     */
    private GoogleAuthResponse signedIn(User user) {

        return GoogleAuthResponse.builder()
                .registrationRequired(false)
                .token(jwtService.generateToken(user.getEmail()))
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }
}
