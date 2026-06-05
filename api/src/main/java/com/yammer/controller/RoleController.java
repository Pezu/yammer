package com.yammer.controller;

import com.yammer.dto.RoleRequest;
import com.yammer.dto.RoleResponse;
import com.yammer.service.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public List<RoleResponse> list() {
        return roleService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER')")
    public RoleResponse create(@Valid @RequestBody RoleRequest request) {
        return roleService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER')")
    public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER')")
    public void delete(@PathVariable UUID id) {
        roleService.delete(id);
    }
}
