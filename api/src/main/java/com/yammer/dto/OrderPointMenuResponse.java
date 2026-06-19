package com.yammer.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * An order point plus its (default) menu tree, for the waiter ordering screen.
 *
 * @param payLater       pay-later (table) flow vs immediate POS flow (pay on placement)
 * @param protocol       protocol (comp/house) order point
 * @param paymentMethods accepted payment methods (CASH/CARD); empty = all — for the immediate flow
 * @param menuId         the order point's default menu (the initially-loaded one)
 * @param items          the default menu's tree
 * @param menus          every menu available for the order point's event, so the waiter can switch
 * @param products       every orderable product across all of the event's menus, for the search box
 */
public record OrderPointMenuResponse(
        UUID orderPointId,
        String orderPointName,
        boolean payLater,
        boolean protocol,
        List<String> paymentMethods,
        UUID menuId,
        List<MenuItemNode> items,
        List<MenuOption> menus,
        List<ProductOption> products) {

    /** A selectable menu (id + display name) for the order screen's menu switcher. */
    public record MenuOption(UUID id, String name) {
    }

    /** A searchable product (a menu item with orderable=true) and the menu it comes from. */
    public record ProductOption(UUID id, String name, BigDecimal price, UUID menuId, String menuName) {
    }
}
