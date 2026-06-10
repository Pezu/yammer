package com.yammer.dto;

import java.math.BigDecimal;

/** Per waiter totals for the report window. */
public record WaiterReportRow(String name, long orders, BigDecimal sales) {
}
