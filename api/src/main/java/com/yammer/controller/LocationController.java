package com.yammer.controller;

import com.yammer.dto.LocationRequest;
import com.yammer.dto.LocationResponse;
import com.yammer.service.LocationService;
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
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    public List<LocationResponse> list(@RequestParam(required = false) UUID clientId) {
        return locationService.list(clientId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocationResponse create(@Valid @RequestBody LocationRequest request) {
        return locationService.create(request);
    }

    @PutMapping("/{id}")
    public LocationResponse update(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        return locationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        locationService.delete(id);
    }
}
