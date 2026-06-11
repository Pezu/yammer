package com.yammer.dto;

import java.util.List;

/** A single page of results plus the total count, for server-side paginated endpoints. */
public record PagedResponse<T>(List<T> content, long total, int page, int size) {
}
