package com.yammer.dto;

/** One row of the products report: a product name and the total ordered quantity. */
public record ProductReportRow(String name, long quantity) {
}
