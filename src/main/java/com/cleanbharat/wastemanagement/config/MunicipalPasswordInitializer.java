package com.cleanbharat.wastemanagement.config;

import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.service.MunicipalCorporationServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gives every already-registered Municipal Corporation a usable login.
 *
 * The corporations were entered by the admin long before they could sign in,
 * so their rows have no password at all. Without this back-fill the municipal
 * bodies that already exist in the database (Mohali, Pune, Chennai, ...) would
 * be permanently locked out of their own dashboard, while newly created ones
 * would work fine.
 *
 * Runs once on every startup but only touches rows whose password is still
 * missing, so a corporation that has already chosen its own password is never
 * reset back to the default.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MunicipalPasswordInitializer implements CommandLineRunner {

    private final MunicipalCorporationRepository municipalCorporationRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        // Only the rows that never received a password
        List<MunicipalCorporation> withoutPassword = municipalCorporationRepository.findAll()
                .stream()
                .filter(corporation -> corporation.getPassword() == null
                        || corporation.getPassword().isBlank())
                .toList();

        if (withoutPassword.isEmpty()) {
            return; // Nothing to migrate, normal case after the first startup
        }

        // Same shared default the admin form uses for brand new corporations
        String encodedDefault = passwordEncoder
                .encode(MunicipalCorporationServiceImpl.DEFAULT_MUNICIPAL_PASSWORD);

        withoutPassword.forEach(corporation -> corporation.setPassword(encodedDefault));
        municipalCorporationRepository.saveAll(withoutPassword);

        // Count only: the credential itself is never written to the logs
        log.info("Initialised the default login password for {} Municipal Corporation(s).",
                withoutPassword.size());
    }
}