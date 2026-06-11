package com.yammer.service;

import com.yammer.dto.CreateOrderPointsBatchRequest;
import com.yammer.dto.MenuItemNode;
import com.yammer.dto.OrderPointMenuResponse;
import com.yammer.dto.OrderPointRequest;
import com.yammer.dto.OrderPointResponse;
import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.IntegrationType;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.MenuEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.repository.IntegrationRepository;
import com.yammer.repository.MenuRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.security.AccessGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderPointService {

    private final OrderPointRepository orderPointRepository;
    private final MenuRepository menuRepository;
    private final IntegrationRepository integrationRepository;
    private final MenuService menuService;
    private final AccessGuard accessGuard;

    @Transactional(readOnly = true)
    public List<OrderPointResponse> listByLocation(UUID locationId, UUID eventId) {
        requireAccessibleLocation(locationId);
        List<OrderPointEntity> points = eventId != null
                ? orderPointRepository.findByLocationIdAndEventIdOrderByName(locationId, eventId)
                : orderPointRepository.findByLocationIdOrderByName(locationId);
        return points.stream().map(OrderPointResponse::from).toList();
    }

    /** The order point plus its menu tree (for the waiter ordering screen). */
    @Transactional(readOnly = true)
    public OrderPointMenuResponse getMenu(UUID id) {
        OrderPointEntity op = requireAccessibleOrderPoint(id);
        List<MenuItemNode> items =
                op.getMenuId() == null ? List.of() : menuService.getTree(op.getMenuId());
        return new OrderPointMenuResponse(op.getId(), op.getName(), op.getMenuId(), items);
    }

    public OrderPointResponse create(OrderPointRequest request) {
        requireAccessibleLocation(request.locationId());
        OrderPointEntity entity = new OrderPointEntity();
        entity.setLocationId(request.locationId());
        entity.setEventId(request.eventId());
        entity.setName(request.name().trim());
        entity.setPayLater(request.payLater());
        entity.setProtocol(request.protocol());
        entity.setMenuId(resolveMenu(request.locationId(), request.menuId()));
        entity.setServiceOrderPointId(
                resolveService(request.locationId(), request.payLater(), request.serviceOrderPointId()));
        entity.setPrinterId(resolveDevice(request.locationId(), request.printerId(), IntegrationType.PRINTER));
        entity.setCashRegisterId(
                resolveDevice(request.locationId(), request.cashRegisterId(), IntegrationType.CASH_REGISTER));
        return OrderPointResponse.from(orderPointRepository.save(entity));
    }

    /** Creates {@code count} order points at once, auto-naming them (B{n}, or M{n}.1 for pay-later). */
    public List<OrderPointResponse> createBatch(CreateOrderPointsBatchRequest request) {
        requireAccessibleLocation(request.locationId());
        if (request.count() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "count must be at least 1");
        }
        UUID menuId = resolveMenu(request.locationId(), request.menuId());
        UUID serviceId =
                resolveService(request.locationId(), request.payLater(), request.serviceOrderPointId());
        UUID printerId = resolveDevice(request.locationId(), request.printerId(), IntegrationType.PRINTER);
        UUID cashRegisterId =
                resolveDevice(request.locationId(), request.cashRegisterId(), IntegrationType.CASH_REGISTER);

        List<OrderPointEntity> existing = request.eventId() != null
                ? orderPointRepository.findByLocationIdAndEventIdOrderByName(request.locationId(), request.eventId())
                : orderPointRepository.findByLocationIdOrderByName(request.locationId());
        List<String> names = generateNames(existing, request.count(), request.payLater());

        List<OrderPointEntity> toCreate = new ArrayList<>(names.size());
        for (String name : names) {
            OrderPointEntity entity = new OrderPointEntity();
            entity.setLocationId(request.locationId());
            entity.setEventId(request.eventId());
            entity.setName(name);
            entity.setPayLater(request.payLater());
            entity.setMenuId(menuId);
            entity.setServiceOrderPointId(serviceId);
            entity.setPrinterId(printerId);
            entity.setCashRegisterId(cashRegisterId);
            toCreate.add(entity);
        }
        return orderPointRepository.saveAll(toCreate).stream().map(OrderPointResponse::from).toList();
    }

    public OrderPointResponse update(UUID id, OrderPointRequest request) {
        OrderPointEntity entity = requireAccessibleOrderPoint(id);
        entity.setName(request.name().trim());
        entity.setPayLater(request.payLater());
        entity.setProtocol(request.protocol());
        entity.setMenuId(resolveMenu(entity.getLocationId(), request.menuId()));
        entity.setServiceOrderPointId(
                resolveService(entity.getLocationId(), request.payLater(), request.serviceOrderPointId()));
        entity.setPrinterId(resolveDevice(entity.getLocationId(), request.printerId(), IntegrationType.PRINTER));
        entity.setCashRegisterId(
                resolveDevice(entity.getLocationId(), request.cashRegisterId(), IntegrationType.CASH_REGISTER));
        return OrderPointResponse.from(orderPointRepository.save(entity));
    }

    public void delete(UUID id) {
        OrderPointEntity entity = requireAccessibleOrderPoint(id);
        orderPointRepository.delete(entity);
    }

    private static final Pattern SPLIT_NAME = Pattern.compile("^([A-Za-z]+\\d+)\\.(\\d+)$");

    /**
     * Splits a pay-later point named {@code M{n}.{m}} into a new sibling {@code M{n}.{max+1}}
     * at the same location, carrying over the menu. Only pay-later points can be split.
     */
    public OrderPointResponse split(UUID id) {
        OrderPointEntity source = requireAccessibleOrderPoint(id);
        if (!source.isPayLater()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pay-later order points can be split");
        }
        Matcher m = SPLIT_NAME.matcher(source.getName());
        if (!m.matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Order point name does not match the M{n}.{m} pattern: " + source.getName());
        }
        String prefix = m.group(1) + ".";

        Pattern siblingPattern = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)$");
        int maxSuffix = 0;
        List<OrderPointEntity> siblings = source.getEventId() != null
                ? orderPointRepository.findByLocationIdAndEventIdOrderByName(source.getLocationId(), source.getEventId())
                : orderPointRepository.findByLocationIdOrderByName(source.getLocationId());
        for (OrderPointEntity op : siblings) {
            Matcher sm = siblingPattern.matcher(op.getName());
            if (sm.matches()) {
                maxSuffix = Math.max(maxSuffix, Integer.parseInt(sm.group(1)));
            }
        }

        OrderPointEntity newOp = new OrderPointEntity();
        newOp.setLocationId(source.getLocationId());
        newOp.setEventId(source.getEventId());
        newOp.setName(prefix + (maxSuffix + 1));
        newOp.setPayLater(true);
        newOp.setProtocol(source.isProtocol());
        newOp.setMenuId(source.getMenuId());
        newOp.setServiceOrderPointId(source.getServiceOrderPointId());
        newOp.setPrinterId(source.getPrinterId());
        newOp.setCashRegisterId(source.getCashRegisterId());
        return OrderPointResponse.from(orderPointRepository.save(newOp));
    }

    // --- name generation (mirrors the servio scheme) ---

    private static final Pattern PAY_LATER_NAME = Pattern.compile("^M(\\d+)\\.(\\d+)$");
    private static final Pattern NON_PAY_LATER_NAME = Pattern.compile("^B(\\d+)$");

    /** Pay-later points are named M{n}.1; the rest B{n}. Numbering continues past the current max. */
    private List<String> generateNames(List<OrderPointEntity> existing, int count, boolean payLater) {
        List<String> names = new ArrayList<>(count);
        Pattern pattern = payLater ? PAY_LATER_NAME : NON_PAY_LATER_NAME;
        int max = 0;
        for (OrderPointEntity op : existing) {
            Matcher m = pattern.matcher(op.getName());
            if (m.matches()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        for (int i = 1; i <= count; i++) {
            names.add(payLater ? "M" + (max + i) + ".1" : "B" + (max + i));
        }
        return names;
    }

    // --- helpers ---

    /** A menu (if any) must belong to the same location as the order point. */
    private UUID resolveMenu(UUID locationId, UUID menuId) {
        if (menuId == null) {
            return null;
        }
        MenuEntity menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown menu: " + menuId));
        if (!Objects.equals(menu.getLocationId(), locationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menu does not belong to this location");
        }
        return menuId;
    }

    /**
     * A service point only applies to a pay-later point; it must be a non-pay-later point
     * in the same location. Cleared (null) for non-pay-later points.
     */
    private UUID resolveService(UUID locationId, boolean payLater, UUID serviceId) {
        if (!payLater || serviceId == null) {
            return null;
        }
        OrderPointEntity service = orderPointRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown service order point: " + serviceId));
        if (!Objects.equals(service.getLocationId(), locationId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Service order point does not belong to this location");
        }
        if (service.isPayLater()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Service order point must not be pay-later");
        }
        return serviceId;
    }

    /** A printer / cash register (if any) must be an integration of that type at the same location. */
    private UUID resolveDevice(UUID locationId, UUID integrationId, IntegrationType type) {
        if (integrationId == null) {
            return null;
        }
        IntegrationEntity device = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown integration: " + integrationId));
        if (!Objects.equals(device.getLocationId(), locationId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Integration does not belong to this location");
        }
        if (device.getType() != type) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Integration is not a " + type);
        }
        return integrationId;
    }

    private OrderPointEntity requireAccessibleOrderPoint(UUID id) {
        return accessGuard.requireAccessibleOrderPoint(id);
    }

    private LocationEntity requireAccessibleLocation(UUID locationId) {
        return accessGuard.requireAccessibleLocation(locationId);
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found: " + id);
    }
}
