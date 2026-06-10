package com.yammer.controller;

import com.yammer.dto.AssignmentRequest;
import com.yammer.dto.MyTableResponse;
import com.yammer.dto.OrderResponse;
import com.yammer.dto.ParentAssignmentResponse;
import com.yammer.dto.PaymentSummaryResponse;
import com.yammer.dto.TableStatsResponse;
import com.yammer.service.OrderPointAssignmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order-point-assignments")
@RequiredArgsConstructor
public class OrderPointAssignmentController {

    private final OrderPointAssignmentService assignmentService;

    @GetMapping
    public List<ParentAssignmentResponse> list(
            @RequestParam UUID locationId, @RequestParam(required = false) UUID eventId) {
        return assignmentService.list(locationId, eventId);
    }

    /** Order points assigned to the currently authenticated user (for the waiter app). */
    @GetMapping("/mine")
    public List<MyTableResponse> mine() {
        return assignmentService.myOrderPoints();
    }

    /** Per-table takings for the current user (waiter Statistics page). */
    @GetMapping("/stats")
    public List<TableStatsResponse> stats() {
        return assignmentService.myStats();
    }

    /** All payments at the current user's tables (waiter Payments page). */
    @GetMapping("/payments")
    public List<PaymentSummaryResponse> payments() {
        return assignmentService.myPayments();
    }

    /** Undelivered orders at the current user's tables (waiter Orders page), optionally by status. */
    @GetMapping("/orders")
    public List<OrderResponse> orders(@RequestParam(required = false) String status) {
        return assignmentService.myOrders(status);
    }

    /** Service kanban: undelivered orders routed to the current user's assigned service points. */
    @GetMapping("/service-board")
    public List<OrderResponse> serviceBoard() {
        return assignmentService.serviceBoard();
    }

    /** Retry fiscalization for a failed payment (currently only logs the payment id). */
    @PostMapping("/payments/{id}/retry-fiscal")
    public void retryFiscal(@PathVariable UUID id) {
        assignmentService.retryFiscal(id);
    }

    @PutMapping
    public ParentAssignmentResponse set(@Valid @RequestBody AssignmentRequest request) {
        return assignmentService.set(request);
    }
}
