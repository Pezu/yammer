package com.yammer.service;

import com.yammer.dto.IntegrationRequest;
import com.yammer.dto.IntegrationResponse;
import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.IntegrationType;
import com.yammer.entity.LocationEntity;
import com.yammer.repository.IntegrationRepository;
import com.yammer.security.AccessGuard;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class IntegrationService {

    private final IntegrationRepository integrationRepository;
    private final AccessGuard accessGuard;

    @Transactional(readOnly = true)
    public List<IntegrationResponse> listByLocation(UUID locationId, IntegrationType type) {
        requireAccessibleLocation(locationId);
        List<IntegrationEntity> rows = type != null
                ? integrationRepository.findByLocationIdAndTypeOrderByName(locationId, type)
                : integrationRepository.findByLocationIdOrderByName(locationId);
        return rows.stream().map(IntegrationResponse::from).toList();
    }

    public IntegrationResponse create(IntegrationRequest request) {
        requireAccessibleLocation(request.locationId());
        IntegrationEntity entity = new IntegrationEntity();
        entity.setLocationId(request.locationId());
        apply(entity, request);
        return IntegrationResponse.from(integrationRepository.save(entity));
    }

    public IntegrationResponse update(UUID id, IntegrationRequest request) {
        IntegrationEntity entity = requireAccessibleIntegration(id);
        apply(entity, request);
        return IntegrationResponse.from(integrationRepository.save(entity));
    }

    public void delete(UUID id) {
        IntegrationEntity entity = requireAccessibleIntegration(id);
        integrationRepository.delete(entity);
    }

    // --- helpers ---

    private void apply(IntegrationEntity entity, IntegrationRequest request) {
        entity.setName(request.name().trim());
        String ip = request.ip() == null || request.ip().isBlank() ? null : request.ip().trim();
        entity.setIp(ip);
        entity.setType(request.type());
    }

    private IntegrationEntity requireAccessibleIntegration(UUID id) {
        IntegrationEntity entity = integrationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Integration not found: " + id));
        requireAccessibleLocation(entity.getLocationId());
        return entity;
    }

    private LocationEntity requireAccessibleLocation(UUID locationId) {
        return accessGuard.requireAccessibleLocation(locationId);
    }
}
