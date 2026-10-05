package com.btc.expenseservice.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Company travel policy for a period: optional per-expense limits by category and an optional trip limit. */
@Entity
@Table(name = "travel_policies", indexes = @Index(name = "idx_travel_policies_effective", columnList = "effective_from, effective_to"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravelPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Maximum total of all expenses on one trip; null = no trip limit. */
    @Column(name = "trip_limit", precision = 12, scale = 2)
    private BigDecimal tripLimit;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Inclusive; null = open-ended. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "travel_policy_limits", joinColumns = @JoinColumn(name = "policy_id"))
    @MapKeyColumn(name = "category", length = 40)
    @Column(name = "limit_amount", nullable = false, precision = 12, scale = 2)
    private Map<String, BigDecimal> categoryLimits = new TreeMap<>();

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
