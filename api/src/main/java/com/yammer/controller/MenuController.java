package com.yammer.controller;

import com.yammer.dto.MenuItemNode;
import com.yammer.dto.MenuRequest;
import com.yammer.dto.MenuResponse;
import com.yammer.service.MenuService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/menus")
    public List<MenuResponse> listMenus(
            @RequestParam UUID locationId, @RequestParam(required = false) UUID eventId) {
        return menuService.listByLocation(locationId, eventId);
    }

    @PostMapping("/menus")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuResponse createMenu(@Valid @RequestBody MenuRequest request) {
        return menuService.create(request);
    }

    @DeleteMapping("/menus/{menuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenu(@PathVariable UUID menuId) {
        menuService.delete(menuId);
    }

    @GetMapping("/menus/{menuId}/tree")
    public List<MenuItemNode> getTree(@PathVariable UUID menuId) {
        return menuService.getTree(menuId);
    }

    @PutMapping("/menus/{menuId}/tree")
    public List<MenuItemNode> saveTree(@PathVariable UUID menuId, @RequestBody List<MenuItemNode> nodes) {
        return menuService.saveTree(menuId, nodes);
    }
}
