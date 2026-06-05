package com.yammer.dto;

import java.util.List;
import java.util.UUID;

/** An order point plus its menu tree, for the waiter ordering screen. */
public record OrderPointMenuResponse(
        UUID orderPointId, String orderPointName, UUID menuId, List<MenuItemNode> items) {
}
