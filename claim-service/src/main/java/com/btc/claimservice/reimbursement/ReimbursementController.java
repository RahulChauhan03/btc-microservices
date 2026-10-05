package com.btc.claimservice.reimbursement;

import com.btc.claimservice.security.CurrentUser;
import com.btc.claimservice.web.PageRequests;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReimbursementController {

    private static final Set<String> SORTABLE = Set.of("id", "updatedAt", "createdAt", "amount", "status");

    private final ReimbursementService reimbursementService;

    /** Employees: their own; administrators: everyone, or one owner via ownerId. */
    @GetMapping("/claims/reimbursements")
    public ResponseEntity<List<ReimbursementDto>> list(@RequestParam(required = false) Long ownerId,
                                                       @RequestParam(required = false) ReimbursementStatus status,
                                                       @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       @RequestParam(defaultValue = "updatedAt,desc") String sort,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return PageRequests.toResponse(reimbursementService.list(CurrentUser.from(jwt), ownerId, status,
                PageRequests.of(page, size, sort, SORTABLE)));
    }

    @GetMapping("/claims/{claimId}/reimbursement")
    public ResponseEntity<ReimbursementDto> forClaim(@PathVariable Long claimId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(reimbursementService.forClaim(claimId, CurrentUser.from(jwt)));
    }

    /** Administrators only, never for their own claim (checked in ReimbursementService). */
    @PutMapping("/claims/{claimId}/reimbursement")
    public ResponseEntity<ReimbursementDto> update(@PathVariable Long claimId, @Valid @RequestBody ReimbursementUpdateDto request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(reimbursementService.update(claimId, request, CurrentUser.from(jwt)));
    }
}
