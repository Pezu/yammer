import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface PagedResponse<T> {
  content: T[];
  total: number;
  page: number;
  size: number;
}

export interface OrderItem {
  id: string;
  menuItemId: string | null;
  name: string;
  price: number | null;
  quantity: number;
  paid: boolean;
}

export interface Order {
  id: string;
  orderNo: number;
  orderPointId: string;
  orderPointName: string;
  eventId: string | null;
  createdBy: string | null;
  createdAt: string;
  status: string;
  items: OrderItem[];
  total: number;
  paid: 'NOT' | 'PAR' | 'PAID';
}

export interface EventOption {
  id: string;
  clientId: string;
  locationId: string;
  name: string;
  startDate: string;
  endDate: string;
}

export interface OrderPointOption {
  id: string;
  name: string;
}

export interface OrderFilterOptions {
  orderPoints: OrderPointOption[];
  waiters: string[];
}

/** Optional server-side filters for the paginated orders list. */
export interface OrderFilters {
  eventId?: string | null;
  orderNo?: number | null;
  orderPointId?: string | null;
  waiter?: string | null;
  status?: string | null;
  paid?: string | null;
}

export interface ProductReportRow {
  name: string;
  quantity: number;
}

export interface PaymentReportRow {
  id: string;
  createdAt: string;
  orderPoint: string;
  method: string;
  amount: number;
  tip: number;
  fiscalStatus: string;
  receiptNumber: string | null;
  createdBy: string | null;
}

export interface PaymentFilters {
  eventId?: string | null;
  method?: string | null;
  orderPointId?: string | null;
  createdBy?: string | null;
  fiscalStatus?: string | null;
}

export interface PaymentFilterOptions {
  methods: string[];
  orderPoints: { id: string; name: string }[];
  users: string[];
  fiscalStatuses: string[];
}

export interface SalesIntervalRow {
  interval: string;
  amountOrdered: number;
  amountPaid: number;
  amountProtocol: number;
  orderCount: number;
}

export interface SalesSummary {
  totalSales: number;
  totalPaid: number;
  totalProtocol: number;
  remainingToPay: number;
  remainingProtocol: number;
}

export interface TableReportRow {
  name: string;
  ordered: number;
  orderedPaid: number;
  orderedProtocol: number;
  paidCash: number;
  paidCard: number;
  protocol: number;
  remaining: number;
  remainingProtocol: number;
}

export interface WaiterReportRow {
  name: string;
  orders: number;
  sales: number;
  unsettledPaid: number;
  unsettledProtocol: number;
}

export interface WaiterTableRow {
  waiter: string;
  table: string;
  ordered: number;
  paid: number;
  tip: number;
  protocol: number;
}

export interface FinalReportRow {
  userName: string;
  table: string;
  paidCard: number;
  paidCash: number;
  tipCard: number;
  tipCash: number;
}

@Injectable({ providedIn: 'root' })
export class OrderReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/orders`;

  /** One page of orders (newest first), paginated and filtered on the server. */
  listPaged(page: number, size: number, filters: OrderFilters = {}): Observable<PagedResponse<Order>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.eventId) params = params.set('eventId', filters.eventId);
    if (filters.orderNo != null) params = params.set('orderNo', filters.orderNo);
    if (filters.orderPointId) params = params.set('orderPointId', filters.orderPointId);
    if (filters.waiter) params = params.set('waiter', filters.waiter);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.paid) params = params.set('paid', filters.paid);
    return this.http.get<PagedResponse<Order>>(`${this.baseUrl}/page`, { params });
  }

  /** Every event the caller can see — backs the orders-report event filter. */
  listEvents(): Observable<EventOption[]> {
    return this.http.get<EventOption[]>(`${environment.apiUrl}/events`);
  }

  /** Order-point and waiter options for the orders-report filter combos. */
  filterOptions(eventId?: string | null): Observable<OrderFilterOptions> {
    let params = new HttpParams();
    if (eventId) params = params.set('eventId', eventId);
    return this.http.get<OrderFilterOptions>(`${this.baseUrl}/filter-options`, { params });
  }

  /** Update an order's unpaid item quantities (quantity ≤ 0 deletes the item). */
  updateItems(orderId: string, items: { id: string; quantity: number }[]): Observable<Order> {
    return this.http.patch<Order>(`${this.baseUrl}/${orderId}/items`, { items });
  }

  /** Delete an order entirely (only allowed when nothing is paid). */
  deleteOrder(orderId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${orderId}`);
  }

  products(eventId?: string | null): Observable<ProductReportRow[]> {
    return this.http.get<ProductReportRow[]>(`${this.baseUrl}/products-report`, {
      params: this.eventParams(eventId),
    });
  }

  /** One page of payments (newest first), filtered server-side. */
  paymentsPaged(
    page: number,
    size: number,
    filters: PaymentFilters = {},
  ): Observable<PagedResponse<PaymentReportRow>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.eventId) params = params.set('eventId', filters.eventId);
    if (filters.method) params = params.set('method', filters.method);
    if (filters.orderPointId) params = params.set('orderPointId', filters.orderPointId);
    if (filters.createdBy) params = params.set('createdBy', filters.createdBy);
    if (filters.fiscalStatus) params = params.set('fiscalStatus', filters.fiscalStatus);
    return this.http.get<PagedResponse<PaymentReportRow>>(`${this.baseUrl}/payments-report/page`, {
      params,
    });
  }

  /** Distinct filter values (method / order point / user / fiscal) for the payments report. */
  paymentFilterOptions(eventId?: string | null): Observable<PaymentFilterOptions> {
    return this.http.get<PaymentFilterOptions>(`${this.baseUrl}/payments-filter-options`, {
      params: this.eventParams(eventId),
    });
  }

  /** Re-queue fiscalization for a failed payment. */
  retryFiscal(paymentId: string): Observable<unknown> {
    return this.http.post(
      `${environment.apiUrl}/order-point-assignments/payments/${paymentId}/retry-fiscal`,
      {},
    );
  }

  sales(eventId?: string | null): Observable<SalesIntervalRow[]> {
    return this.http.get<SalesIntervalRow[]>(`${this.baseUrl}/sales-report`, {
      params: this.eventParams(eventId),
    });
  }

  salesSummary(eventId?: string | null): Observable<SalesSummary> {
    return this.http.get<SalesSummary>(`${this.baseUrl}/sales-summary`, {
      params: this.eventParams(eventId),
    });
  }

  tablesReport(eventId?: string | null): Observable<TableReportRow[]> {
    return this.http.get<TableReportRow[]>(`${this.baseUrl}/tables-report`, {
      params: this.eventParams(eventId),
    });
  }

  waitersReport(eventId?: string | null): Observable<WaiterReportRow[]> {
    return this.http.get<WaiterReportRow[]>(`${this.baseUrl}/waiters-report`, {
      params: this.eventParams(eventId),
    });
  }

  waiterTablesReport(eventId?: string | null): Observable<WaiterTableRow[]> {
    return this.http.get<WaiterTableRow[]>(`${this.baseUrl}/waiter-tables-report`, {
      params: this.eventParams(eventId),
    });
  }

  /** Final report: per user + order point, card/cash paid and tip (protocol excluded). */
  finalReport(eventId?: string | null): Observable<FinalReportRow[]> {
    return this.http.get<FinalReportRow[]>(`${this.baseUrl}/final-report`, {
      params: this.eventParams(eventId),
    });
  }

  private eventParams(eventId?: string | null): HttpParams {
    return eventId ? new HttpParams().set('eventId', eventId) : new HttpParams();
  }
}
