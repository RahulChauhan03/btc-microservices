package com.btc.claimservice.audit;

import com.btc.claimservice.security.CurrentUser;
import com.btc.claimservice.web.PageRequests;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, administrator-only search of claim and reimbursement audit records. */
@RestController
@RequestMapping("/claims/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private static final Set<String> SORTABLE = Set.of("id", "createdAt", "action", "actorId");

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<List<AuditLogDto>> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal Jwt jwt) {
        return PageRequests.toResponse(auditService.search(CurrentUser.from(jwt), action, actorId, targetId, from, to,
                PageRequests.of(page, size, sort, SORTABLE)));
    }
}
