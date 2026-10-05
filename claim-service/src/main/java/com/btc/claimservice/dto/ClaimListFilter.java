package com.btc.claimservice.dto;

import java.util.List;

/** Optional list filters; ownerId is honoured for administrators only (see CurrentUser.scopeOwner). */
public record ClaimListFilter(Long ownerId, Long tripId, List<String> statuses) {
}
