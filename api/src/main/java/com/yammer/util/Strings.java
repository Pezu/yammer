package com.yammer.util;

/** Small string helpers shared across request→entity mapping. */
public final class Strings {

    private Strings() {
    }

    /** Trims the value; returns {@code null} for null/blank input (optional-string normalization). */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
