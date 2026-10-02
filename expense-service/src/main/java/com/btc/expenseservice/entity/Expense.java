package com.btc.expenseservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "expenses", indexes = {
        @Index(name = "idx_expenses_owner_id", columnList = "owner_id"),
        @Index(name = "idx_expenses_trip_id", columnList = "trip_id"),
        @Index(name = "idx_expenses_claim_id", columnList = "claim_id")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private LocalDate expenseDate;

    /** Owning user id (users.id in user-service). Null only for rows created before ownership existed. */
    @Column(name = "owner_id")
    private Long ownerId;

    /** Optional trip (trips.id in trip-service, owned by the same user). */
    @Column(name = "trip_id")
    private Long tripId;

    /**
     * The claim currently covering this expense (claims.id in claim-service), or null. Set and cleared only
     * by ExpenseLockService; while set, the expense cannot be edited or deleted.
     */
    @Column(name = "claim_id")
    private Long claimId;

    /** Optimistic locking: a stale edit cannot overwrite a concurrent claim lock or change. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
