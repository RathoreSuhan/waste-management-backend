package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.RegisterRequest;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.*;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.util.LocationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.cleanbharat.wastemanagement.dto.AuthResponse;
import com.cleanbharat.wastemanagement.dto.LoginRequest;
import com.cleanbharat.wastemanagement.security.JwtService;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final MunicipalCorporationRepository municipalCorporationRepository; // municipal bodies live in their own table
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;


    public String register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        // The official email of a city's corporation can never become a normal account
        if (municipalCorporationRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        // Prevent self-registration as ADMIN
        if (request.getRole() == Role.ROLE_ADMIN) {
            throw new UnauthorizedRegistrationException("Admin registration is not allowed.");
        }

        // Municipal dashboard access is not something a user can sign up for.
        // Only the email the admin saved for that city can open the Municipal Console.
        if (request.getRole() == Role.ROLE_MUNICIPAL_OFFICER) {
            throw new UnauthorizedRegistrationException(
                    "Municipal registration is not allowed. Municipal bodies sign in with the official email registered by the admin.");
        }

        // Validation for cleaners
        if (request.getRole() == Role.ROLE_CLEANER && request.getCleanerType() == null) {
            throw new InvalidRegistrationException("Cleaner type is required for ROLE_CLEANER.");
        }

        // Create User
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .cleanerType(request.getCleanerType())
                .organizationName(request.getOrganizationName())
                .state(LocationUtil.normalizeLocation(request.getState()))
                .city(LocationUtil.normalizeLocation(request.getCity()))
                .rewardPoints(0)
                .build();

        userRepository.save(user);
        return "User Registered Successfully";
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
}
