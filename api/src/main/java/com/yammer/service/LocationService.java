package com.yammer.service;

import com.yammer.dto.LocationRequest;
import com.yammer.dto.LocationResponse;
import com.yammer.entity.LocationEntity;
import com.yammer.repository.ClientRepository;
import com.yammer.repository.LocationRepository;
import com.yammer.security.AccessGuard;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final ClientRepository clientRepository;
    private final CurrentUserProvider currentUser;
    private final AccessGuard accessGuard;

    /**
     * SUPER sees all locations; everyone else only those in their own client.
     * When {@code clientId} is given, results are restricted to that client (a non-SUPER
     * caller asking for a client other than their own gets nothing).
     */
    public List<LocationResponse> list(UUID clientId) {
        UserPrincipal me = currentUser.require();
        if (clientId != null) {
            if (!me.isSuper() && !Objects.equals(clientId, me.clientId())) {
                return List.of();
            }
            return locationRepository.findByClientIdOrderByName(clientId).stream()
                    .map(LocationResponse::from)
                    .toList();
        }
        List<LocationEntity> locations = me.isSuper()
                ? locationRepository.findAll(Sort.by("name"))
                : me.clientId() == null
                        ? List.of()
                        : locationRepository.findByClientIdOrderByName(me.clientId());
        return locations.stream().map(LocationResponse::from).toList();
    }

    public LocationResponse create(LocationRequest request) {
        UserPrincipal me = currentUser.require();
        LocationEntity entity = new LocationEntity();
        entity.setName(request.name().trim());
        entity.setClientId(resolveClient(me, request.clientId()));
        return LocationResponse.from(locationRepository.save(entity));
    }

    public LocationResponse update(UUID id, LocationRequest request) {
        UserPrincipal me = currentUser.require();
        LocationEntity entity = accessGuard.requireAccessibleLocation(id);
        entity.setName(request.name().trim());
        entity.setClientId(resolveClient(me, request.clientId()));
        return LocationResponse.from(locationRepository.save(entity));
    }

    public void delete(UUID id) {
        LocationEntity entity = accessGuard.requireAccessibleLocation(id);
        locationRepository.delete(entity);
    }

    /** A non-SUPER caller is forced to their own client; SUPER picks any existing client. */
    private UUID resolveClient(UserPrincipal me, UUID requested) {
        if (!me.isSuper()) {
            if (me.clientId() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not linked to a client");
            }
            return me.clientId();
        }
        if (requested == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A client is required");
        }
        if (!clientRepository.existsById(requested)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown client: " + requested);
        }
        return requested;
    }
}
