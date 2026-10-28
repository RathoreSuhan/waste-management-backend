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
@Table(
        name = "municipal_corporations", // Custom table name
        uniqueConstraints = {
                // One corporation per ULB: the code-backed routing looks the
                // destination up by this column. PostgreSQL treats NULLs as
                // distinct, so legacy rows without a code coexist freely.
                @UniqueConstraint(
                        name = "uq_municipal_ulb_code",
                        columnNames = "ulb_code")
        }
)
public class MunicipalCorporation {

    @Id // Primary Key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto Increment ID
    private Long id;

    /*
      The corporation's service area as a display name, kept verbatim from
      whatever the administrator registered. For rows created through the
      directory cascade this is the workbook's ULB name; for legacy rows it is
      whatever was typed (e.g. "Mohali") and MUST stay untouched - reports
      without a ULB code still route through this exact value, so renaming it
      would cut those reports off from their destination.
    */
    private String city; // City name

    /*
      The LGD directory's ULB code this corporation is accountable for. NULL
      on rows that have not been mapped yet (the preflight mapping at T3 does
      exactly that, without ever touching city above); a code-backed report
      resolves its destination by this column and only by this column.
    */
    @Column(name = "ulb_code", length = 20)
    private String ulbCode;

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