package com.btc.userservice.audit;

import java.time.LocalDateTime;

public record AuditLogDto(Long id, String source, Long actorId, String action, String targetType, Long targetId,
                          String summary, LocalDateTime createdAt) {

    static AuditLogDto from(AuditLog log, String source) {
        return new AuditLogDto(log.getId(), source, log.getActorId(), log.getAction(), log.getTargetType(),
                log.getTargetId(), log.getSummary(), log.getCreatedAt());
    }
}
