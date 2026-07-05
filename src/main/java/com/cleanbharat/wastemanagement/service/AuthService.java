package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.RegisterRequest;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.*;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.util.LocationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.cleanbharat.wastemanagement.dto.AuthResponse;
import com.cleanbharat.wastemanagement.dto.LoginRequest;
import com.cleanbharat.wastemanagement.security.JwtService;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;


    public String register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        // Prevent self-registration as ADMIN
        if (request.getRole() == Role.ROLE_ADMIN) {
            throw new UnauthorizedRegistrationException("Admin registration is not allowed.");
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
}