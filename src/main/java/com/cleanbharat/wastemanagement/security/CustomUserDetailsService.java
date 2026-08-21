package com.cleanbharat.wastemanagement.security;

import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final MunicipalCorporationRepository municipalCorporationRepository; // municipal bodies are their own login identity

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // A token issued to a city's official email belongs to that corporation,
        // so it is resolved before the normal user table
        Optional<MunicipalCorporation> corporation = municipalCorporationRepository.findByEmailIgnoreCase(email);
        if (corporation.isPresent()) {
            return toMunicipalUserDetails(corporation.get());
        }

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(
                        () -> new UsernameNotFoundException("User not found")
                );


        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }

    /**
     * Gives a registered corporation the municipal authority, which is what lets
     * SecurityConfig's hasRole("MUNICIPAL_OFFICER") rules accept its token.
     */
    private UserDetails toMunicipalUserDetails(MunicipalCorporation corporation) {
        return new org.springframework.security.core.userdetails.User(
                corporation.getEmail(),
                corporation.getPassword() == null ? "" : corporation.getPassword(), // empty hash simply never matches
                List.of(new SimpleGrantedAuthority(Role.ROLE_MUNICIPAL_OFFICER.name()))
        );
    }
}
