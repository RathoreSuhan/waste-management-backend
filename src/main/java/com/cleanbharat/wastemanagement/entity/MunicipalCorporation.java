package com.cleanbharat.wastemanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity // Marks this class as a database entity (table)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder // Builder Pattern support
@Table(name = "municipal_corporations") // Custom table name
public class MunicipalCorporation {

    @Id // Primary Key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto Increment ID
    private Long id;

    private String city; // City name
    private String organizationName; // Municipal corporation name
    private String phone; // Contact phone number
    private String email; // Contact email address

    /*
     * Login password of this corporation, stored as a BCrypt hash.
     *
     * A city has exactly one municipal body, so the corporation row the admin
     * registers IS the login identity for that city's dashboard - nobody can
     * reach it merely by signing up as a "municipal" cleaner. Rows created
     * before this column existed are back-filled with the shared default by
     * MunicipalPasswordInitializer, which is why the column stays nullable at
     * the schema level instead of failing the startup migration.
     */
    private String password;

    @OneToMany(mappedBy = "assignedMunicipalCorporation")
    @Builder.Default
    private List<CleanupAssignment> assignments = new ArrayList<>();
}