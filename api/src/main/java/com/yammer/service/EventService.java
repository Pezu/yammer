package com.yammer.service;

import com.yammer.dto.EventRequest;
import com.yammer.dto.EventResponse;
import com.yammer.entity.EventEntity;
import com.yammer.entity.LocationEntity;
import com.yammer.repository.EventRepository;
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
public class EventService {

    private final EventRepository eventRepository;
    private final AccessGuard accessGuard;

    @Transactional(readOnly = true)
    public List<EventResponse> listByLocation(UUID locationId) {
        requireAccessibleLocation(locationId);
        return eventRepository.findByLocationIdOrderByStartDateDesc(locationId).stream()
                .map(EventResponse::from)
                .toList();
    }

    public EventResponse create(EventRequest request) {
        LocationEntity location = requireAccessibleLocation(request.locationId());
        validateDates(request);
        EventEntity entity = new EventEntity();
        entity.setLocationId(location.getId());
        entity.setClientId(location.getClientId());
        entity.setName(request.name().trim());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        return EventResponse.from(eventRepository.save(entity));
    }

    public EventResponse update(UUID id, EventRequest request) {
        EventEntity entity = requireAccessibleEvent(id);
        validateDates(request);
        entity.setName(request.name().trim());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        return EventResponse.from(eventRepository.save(entity));
    }

    public void delete(UUID id) {
        EventEntity entity = requireAccessibleEvent(id);
        eventRepository.delete(entity);
    }

    private void validateDates(EventRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "End date cannot be before start date");
        }
    }

    private EventEntity requireAccessibleEvent(UUID id) {
        EventEntity entity = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found: " + id));
        requireAccessibleLocation(entity.getLocationId());
        return entity;
    }

    private LocationEntity requireAccessibleLocation(UUID locationId) {
        return accessGuard.requireAccessibleLocation(locationId);
    }
}
