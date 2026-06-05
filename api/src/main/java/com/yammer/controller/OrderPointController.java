package com.yammer.controller;

import com.yammer.dto.CreateOrderPointsBatchRequest;
import com.yammer.dto.OrderPointMenuResponse;
import com.yammer.dto.OrderPointRequest;
import com.yammer.dto.OrderPointResponse;
import com.yammer.service.OrderPointService;
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
@RequestMapping("/order-points")
@RequiredArgsConstructor
public class OrderPointController {

    private final OrderPointService orderPointService;

    @GetMapping
    public List<OrderPointResponse> list(@RequestParam UUID locationId) {
        return orderPointService.listByLocation(locationId);
    }

    @GetMapping("/{id}/menu")
    public OrderPointMenuResponse menu(@PathVariable UUID id) {
        return orderPointService.getMenu(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderPointResponse create(@Valid @RequestBody OrderPointRequest request) {
        return orderPointService.create(request);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<OrderPointResponse> createBatch(@Valid @RequestBody CreateOrderPointsBatchRequest request) {
        return orderPointService.createBatch(request);
    }

    @PutMapping("/{id}")
    public OrderPointResponse update(@PathVariable UUID id, @Valid @RequestBody OrderPointRequest request) {
        return orderPointService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        orderPointService.delete(id);
    }

    @PostMapping("/{id}/split")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderPointResponse split(@PathVariable UUID id) {
        return orderPointService.split(id);
    }
}
