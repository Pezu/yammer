package com.yammer.controller;

import com.yammer.dto.OrderItemsUpdateRequest;
import com.yammer.dto.OrderResponse;
import com.yammer.dto.OrderStatusRequest;
import com.yammer.dto.PlaceOrderRequest;
import com.yammer.dto.ProductReportRow;
import com.yammer.dto.SalesIntervalRow;
import com.yammer.dto.SalesSummaryResponse;
import com.yammer.dto.TableReportRow;
import com.yammer.dto.WaiterReportRow;
import com.yammer.dto.WaiterTableRow;
import com.yammer.service.OrderService;
import com.yammer.service.SalesReportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final SalesReportService salesReportService;

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) java.util.UUID orderPointId) {
        return orderPointId != null ? orderService.listByOrderPoint(orderPointId) : orderService.list();
    }

    /** Aggregated products report: every ordered product with its total quantity. */
    @GetMapping("/products-report")
    public List<ProductReportRow> productsReport() {
        return orderService.productReport();
    }

    /** Sales report bucketed into 10-minute intervals (amount ordered, amount paid, order count). */
    @GetMapping("/sales-report")
    public List<SalesIntervalRow> salesReport() {
        return salesReportService.intervals();
    }

    /** Sales totals: sales, paid, protocol-settled, and what's still outstanding. */
    @GetMapping("/sales-summary")
    public SalesSummaryResponse salesSummary() {
        return salesReportService.summary();
    }

    /** Per order point: ordered, paid cash, paid card, remaining. */
    @GetMapping("/tables-report")
    public List<TableReportRow> tablesReport() {
        return salesReportService.tables();
    }

    /** Per waiter: number of orders and their sales value. */
    @GetMapping("/waiters-report")
    public List<WaiterReportRow> waitersReport() {
        return salesReportService.waiters();
    }

    /** Per waiter and order point: ordered, paid, tip, protocol. */
    @GetMapping("/waiter-tables-report")
    public List<WaiterTableRow> waiterTablesReport() {
        return salesReportService.waiterTables();
    }

    /** Move an order to a new kanban status. */
    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody OrderStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }

    /** Update an order's unpaid item quantities (quantity ≤ 0 deletes the item). */
    @PatchMapping("/{id}/items")
    public OrderResponse updateItems(
            @PathVariable UUID id, @RequestBody OrderItemsUpdateRequest request) {
        return orderService.updateItems(id, request.items());
    }

    /** Delete an order entirely (only when nothing is paid). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        orderService.deleteOrder(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(request);
    }
}
