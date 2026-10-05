package com.btc.claimservice.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "claims", indexes = @Index(name = "idx_claims_owner_id", columnList = "owner_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String claimNumber;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    /** Sum of the linked expenses' amounts, calculated by the server when the claim is saved. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal claimAmount;

    /** A {@link ClaimStatus} name; kept as a string column so legacy values still load. */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    /** Owning user id (users.id in user-service). Null only for rows created before ownership existed. */
    @Column(name = "owner_id")
    private Long ownerId;

    /** The trip shared by all linked expenses, or null when they have none. */
    @Column(name = "trip_id")
    private Long tripId;

    /** Administrator who approved or rejected the claim, taken from their verified token. */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** Optimistic locking: two concurrent reviews (or a review and an edit) cannot both succeed. */
    @Version
    @Column(nullable = false)
    private Long version;

    /** Expense ids (expenses.id in expense-service) covered by this claim. Batch-loaded to avoid N+1 in lists. */
    @Builder.Default
    @BatchSize(size = 100)
    @ElementCollection
    @CollectionTable(name = "claim_expenses",
            joinColumns = @JoinColumn(name = "claim_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_claim_expenses", columnNames = {"claim_id", "expense_id"}),
            indexes = @Index(name = "idx_claim_expenses_expense_id", columnList = "expense_id"))
    @Column(name = "expense_id", nullable = false)
    private Set<Long> expenseIds = new LinkedHashSet<>();

    @PrePersist
    public void prePersist() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
    }
}
