package com.btc.userservice.entity;

import java.util.Locale;

/** Supported roles. Stored as the enum name in users.role. */
public enum Role {
    ADMIN,
    EMPLOYEE;

    /** Lenient read of stored values: anything unknown or empty resolves to the least privileged role. */
    public static Role fromStored(String value) {
        if (value == null || value.isBlank()) {
            return EMPLOYEE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return EMPLOYEE;
        }
    }
}
