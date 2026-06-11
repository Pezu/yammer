package com.yammer.bridge.print;

/**
 * Shared quantity formatter: whole quantities print without a trailing ".0"
 * (e.g. {@code 1} instead of {@code 1.0}).
 */
public final class Qty {

    private Qty() {}

    /** Returns {@code qty} as a string, dropping the ".0" suffix for whole numbers. */
    public static String label(double qty) {
        return qty == Math.floor(qty) ? String.valueOf((long) qty) : String.valueOf(qty);
    }
}
