package com.yammer.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One node of a menu tree, used both ways. On read, {@code id} is populated; on write
 * (save-the-tree) a known {@code id} updates that node in place while a null/unknown id
 * inserts a new one — so node ids stay stable across edits. A category has
 * {@code orderable=false}; a product has {@code orderable=true} and a {@code price}.
 */
public record MenuItemNode(
        UUID id,
        String name,
        boolean orderable,
        BigDecimal price,
        UUID vatTypeId,
        String imageObject,
        boolean combined,
        List<MenuItemNode> children) {
}
