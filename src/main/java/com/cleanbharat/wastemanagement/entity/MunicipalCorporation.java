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

    @OneToMany(mappedBy = "assignedMunicipalCorporation")
    @Builder.Default
    private List<CleanupAssignment> assignments = new ArrayList<>();
}