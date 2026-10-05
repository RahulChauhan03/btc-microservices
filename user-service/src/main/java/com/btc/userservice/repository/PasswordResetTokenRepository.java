package com.btc.userservice.repository;

import com.btc.userservice.entity.PasswordResetToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Marks the token used if, and only if, it is still usable. InnoDB row locking makes this the single point
     * of truth under concurrency: of two simultaneous resets with the same token exactly one gets 1.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id"
            + " and t.usedAt is null and t.revokedAt is null and t.expiresAt > :now")
    int consume(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Revokes every still-open token of the user (a newer request or a completed reset supersedes them). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.revokedAt = :now where t.userId = :userId"
            + " and t.usedAt is null and t.revokedAt is null")
    int revokeOpenTokens(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.revokedAt = :now where t.id = :id and t.usedAt is null and t.revokedAt is null")
    int revoke(@Param("id") Long id, @Param("now") LocalDateTime now);
}
