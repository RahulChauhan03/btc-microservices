package com.btc.userservice.dto;

/** Head counts for the admin dashboard; no personal data. */
public record UserStatsDto(long total, long admins, long employees, long joinedLast30Days) {
}
