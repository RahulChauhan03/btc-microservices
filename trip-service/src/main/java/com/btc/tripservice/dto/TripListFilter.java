package com.btc.tripservice.dto;

import java.util.List;

/** Optional list filters; ownerId is honoured for administrators only (see CurrentUser.scopeOwner). */
public record TripListFilter(Long ownerId, String status, boolean upcoming, List<Long> ids) {

    public static final int MAX_IDS = 100;

    public TripListFilter(Long ownerId, String status, boolean upcoming) {
        this(ownerId, status, upcoming, null);
    }
}
