package com.yammer.controller;

import com.yammer.dto.VatTypeRequest;
import com.yammer.dto.VatTypeResponse;
import com.yammer.service.VatTypeService;
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
@RequestMapping("/vat-types")
@RequiredArgsConstructor
public class VatTypeController {

    private final VatTypeService vatTypeService;

    @GetMapping
    public List<VatTypeResponse> list() {
        return vatTypeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER')")
    public VatTypeResponse create(@Valid @RequestBody VatTypeRequest request) {
        return vatTypeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER')")
    public VatTypeResponse update(@PathVariable UUID id, @Valid @RequestBody VatTypeRequest request) {
        return vatTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER')")
    public void delete(@PathVariable UUID id) {
        vatTypeService.delete(id);
    }
}
