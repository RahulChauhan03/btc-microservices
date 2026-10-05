package com.btc.tripservice.dto;

import java.util.List;

/** Trip counts for the caller's scope; "upcoming" = planned or in progress and not yet ended. */
public record TripSummaryDto(long total, long upcoming, List<StatusCountDto> byStatus) {
}
