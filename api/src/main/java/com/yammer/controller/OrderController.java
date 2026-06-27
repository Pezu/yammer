package com.yammer.controller;

import com.yammer.dto.OrderFilterOptions;
import com.yammer.dto.OrderItemsUpdateRequest;
import com.yammer.dto.OrderPointBillLine;
import com.yammer.dto.OrderResponse;
import com.yammer.dto.FinalReportRow;
import com.yammer.dto.OrderStatusRequest;
import com.yammer.dto.PagedResponse;
import com.yammer.dto.PlaceOrderRequest;
import com.yammer.dto.ProductReportRow;
import com.yammer.dto.SalesIntervalRow;
import com.yammer.dto.SalesSummaryResponse;
import com.yammer.dto.TableReportRow;
import com.yammer.dto.WaiterReportRow;
import com.yammer.dto.PaymentFilterOptions;
import com.yammer.dto.PaymentReportRow;
import com.yammer.dto.WaiterTableRow;
import com.yammer.service.OrderService;
import com.yammer.service.SalesReportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /** Financial reports are visible to admins, super-admins and read-only watchers. */
    private static final String REPORTS = "hasAnyRole('ADMIN','SUPER','WATCHER')";

    private final OrderService orderService;
    private final SalesReportService salesReportService;

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) UUID orderPointId) {
        return orderPointId != null ? orderService.listByOrderPoint(orderPointId) : orderService.list();
    }

    /** An order point's bill aggregated per product (paid + unpaid qty) — backs the waiter table screen. */
    @GetMapping("/bill")
    public List<OrderPointBillLine> bill(@RequestParam UUID orderPointId) {
        return orderService.billByOrderPoint(orderPointId);
    }

    /**
     * Server-side paginated order list (newest first) — backs the backoffice orders report.
     * All filters are optional: {@code eventId}, {@code orderNo}, {@code orderPointId},
     * {@code waiter}, {@code status}, {@code paid} (NOT / PAR / PAID).
     */
    @GetMapping("/page")
    public PagedResponse<OrderResponse> listPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) Long orderNo,
            @RequestParam(required = false) UUID orderPointId,
            @RequestParam(required = false) String waiter,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paid) {
        return orderService.listPaged(page, size, eventId, orderNo, orderPointId, waiter, status, paid);
    }

    /** Order-point and waiter option lists for the orders-report filter combos. */
    @GetMapping("/filter-options")
    public OrderFilterOptions filterOptions(@RequestParam(required = false) UUID eventId) {
        return orderService.filterOptions(eventId);
    }

    /** Aggregated products report: every ordered product with its total quantity. */
    @GetMapping("/products-report")
    @PreAuthorize(REPORTS)
    public List<ProductReportRow> productsReport(@RequestParam(required = false) UUID eventId) {
        return orderService.productReport(eventId);
    }

    /** Sales report bucketed into 10-minute intervals (amount ordered, amount paid, order count). */
    @GetMapping("/sales-report")
    @PreAuthorize(REPORTS)
    public List<SalesIntervalRow> salesReport(@RequestParam(required = false) UUID eventId) {
        return salesReportService.intervals(eventId);
    }

    /** Sales totals: sales, paid, protocol-settled, and what's still outstanding. */
    @GetMapping("/sales-summary")
    @PreAuthorize(REPORTS)
    public SalesSummaryResponse salesSummary(@RequestParam(required = false) UUID eventId) {
        return salesReportService.summary(eventId);
    }

    /** Per order point: ordered, paid cash, paid card, remaining. */
    @GetMapping("/tables-report")
    @PreAuthorize(REPORTS)
    public List<TableReportRow> tablesReport(@RequestParam(required = false) UUID eventId) {
        return salesReportService.tables(eventId);
    }

    /** Per waiter: number of orders and their sales value. */
    @GetMapping("/waiters-report")
    @PreAuthorize(REPORTS)
    public List<WaiterReportRow> waitersReport(@RequestParam(required = false) UUID eventId) {
        return salesReportService.waiters(eventId);
    }

    /** Per waiter and order point: ordered, paid, tip, protocol. */
    @GetMapping("/waiter-tables-report")
    @PreAuthorize(REPORTS)
    public List<WaiterTableRow> waiterTablesReport(@RequestParam(required = false) UUID eventId) {
        return salesReportService.waiterTables(eventId);
    }

    /** Final report: per user + order point, card/cash paid and tip (protocol excluded). */
    @GetMapping("/final-report")
    @PreAuthorize(REPORTS)
    public List<FinalReportRow> finalReport(@RequestParam(required = false) UUID eventId) {
        return salesReportService.finalReport(eventId);
    }

    /** One page of the event's payments (newest first), filtered server-side. */
    @GetMapping("/payments-report/page")
    @PreAuthorize(REPORTS)
    public PagedResponse<PaymentReportRow> paymentsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) UUID orderPointId,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) String fiscalStatus) {
        return salesReportService.paymentsPaged(page, size, eventId, method, orderPointId, createdBy, fiscalStatus);
    }

    /** Distinct filter values (method / order point / user / fiscal) for the payments report. */
    @GetMapping("/payments-filter-options")
    @PreAuthorize(REPORTS)
    public PaymentFilterOptions paymentFilterOptions(@RequestParam(required = false) UUID eventId) {
        return salesReportService.paymentFilterOptions(eventId);
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
