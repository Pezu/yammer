package com.yammer.dto;

import java.util.List;

/**
 * Outcome of a bulk customer .xlsx import: how many rows were inserted, how many
 * were skipped (blank or invalid), and a per-row description of every skipped row.
 */
public record CustomerImportResult(int imported, int skipped, List<String> errors) {
}
