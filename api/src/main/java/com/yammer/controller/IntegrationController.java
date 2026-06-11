package com.yammer.controller;

import com.yammer.dto.IntegrationRequest;
import com.yammer.dto.IntegrationResponse;
import com.yammer.entity.IntegrationType;
import com.yammer.service.IntegrationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER')") // device/integration config is admin-only; read relaxed below
public class IntegrationController {

    private final IntegrationService integrationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<IntegrationResponse> list(
            @RequestParam UUID locationId, @RequestParam(required = false) IntegrationType type) {
        return integrationService.listByLocation(locationId, type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IntegrationResponse create(@Valid @RequestBody IntegrationRequest request) {
        return integrationService.create(request);
    }

    @PutMapping("/{id}")
    public IntegrationResponse update(@PathVariable UUID id, @Valid @RequestBody IntegrationRequest request) {
        return integrationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        integrationService.delete(id);
    }
}
