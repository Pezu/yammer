import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AssignedOrderPoint {
  id: string;
  locationId: string;
  name: string;
  payLater: boolean;
  protocol: boolean;
  menuId: string | null;
  serviceOrderPointId: string | null;
  status: 'EMPTY' | 'UNPAID' | 'PAID';
}

/** One node of the menu tree. Category: orderable=false. Product: orderable=true + price. */
export interface MenuNode {
  id: string;
  name: string;
  orderable: boolean;
  price: number | null;
  vatTypeId: string | null;
  children: MenuNode[];
}

export interface OrderPointMenu {
  orderPointId: string;
  orderPointName: string;
  menuId: string | null;
  items: MenuNode[];
}

@Injectable({ providedIn: 'root' })
export class WaiterOrderPointService {
  private readonly http = inject(HttpClient);

  /** Order points whose parent the logged-in waiter is assigned to. */
  mine(): Observable<AssignedOrderPoint[]> {
    return this.http.get<AssignedOrderPoint[]>(
      `${environment.apiUrl}/order-point-assignments/mine`,
    );
  }

  /** Per-table takings for the Statistics page. */
  stats(): Observable<TableStats[]> {
    return this.http.get<TableStats[]>(`${environment.apiUrl}/order-point-assignments/stats`);
  }

  /** All payments at the waiter's tables for the Payments page. */
  paymentsSummary(): Observable<PaymentSummary[]> {
    return this.http.get<PaymentSummary[]>(
      `${environment.apiUrl}/order-point-assignments/payments`,
    );
  }

  /** Retry fiscalization for a failed payment. */
  retryFiscal(paymentId: string): Observable<unknown> {
    return this.http.post(
      `${environment.apiUrl}/order-point-assignments/payments/${paymentId}/retry-fiscal`,
      {},
    );
  }

  /** Undelivered orders at the waiter's tables, optionally filtered to one status. */
  activeOrders(status?: 'ORDERED' | 'READY'): Observable<ActiveOrder[]> {
    const url = `${environment.apiUrl}/order-point-assignments/orders`;
    return status
      ? this.http.get<ActiveOrder[]>(url, { params: new HttpParams().set('status', status) })
      : this.http.get<ActiveOrder[]>(url);
  }

  /** Move an order to a new status (e.g. mark DELIVERED). */
  setOrderStatus(orderId: string, status: string): Observable<unknown> {
    return this.http.patch(`${environment.apiUrl}/orders/${orderId}/status`, { status });
  }

  /** The menu tree for an order point. */
  menu(orderPointId: string): Observable<OrderPointMenu> {
    return this.http.get<OrderPointMenu>(
      `${environment.apiUrl}/order-points/${orderPointId}/menu`,
    );
  }

  /** Place an order at an order point. */
  placeOrder(orderPointId: string, items: PlaceOrderItem[]): Observable<unknown> {
    return this.http.post(`${environment.apiUrl}/orders`, { orderPointId, items });
  }

  /** Orders already placed at an order point (newest first). */
  orders(orderPointId: string): Observable<PlacedOrder[]> {
    const params = new HttpParams().set('orderPointId', orderPointId);
    return this.http.get<PlacedOrder[]>(`${environment.apiUrl}/orders`, { params });
  }

  /** Payments taken at an order point. */
  payments(orderPointId: string): Observable<Payment[]> {
    const params = new HttpParams().set('orderPointId', orderPointId);
    return this.http.get<Payment[]>(`${environment.apiUrl}/payments`, { params });
  }

  pay(
    orderPointId: string,
    amount: number,
    tip: number,
    method: PaymentMethod,
  ): Observable<Payment> {
    return this.http.post<Payment>(`${environment.apiUrl}/payments`, {
      orderPointId,
      amount,
      tip,
      method,
    });
  }

  /** Line-level settle (full or partial, with order-line splitting). */
  payLines(
    orderPointId: string,
    mode: 'FULL' | 'PARTIAL',
    method: PaymentMethod,
    tip: number,
    items: LinePaymentItem[],
  ): Observable<unknown> {
    return this.http.post(`${environment.apiUrl}/payments/lines`, {
      orderPointId,
      mode,
      method,
      tip,
      items,
    });
  }
}

export type PaymentMethod = 'CASH' | 'CARD' | 'PROTOCOL';

export interface Payment {
  id: string;
  orderPointId: string;
  amount: number;
  tip: number;
  method: PaymentMethod;
  createdBy: string | null;
  createdAt: string;
}

export interface PlacedOrderItem {
  id: string;
  menuItemId: string | null;
  name: string;
  price: number | null;
  quantity: number;
  paid: boolean;
}

export interface LinePaymentItem {
  menuItemId: string;
  quantity: number;
}

export interface PaymentSummary {
  id: string;
  orderPointId: string;
  tableName: string;
  amount: number;
  method: PaymentMethod;
  tip: number;
  fiscalStatus: 'FAILED' | 'PENDING' | 'SUCCESS' | 'PROTOCOL';
  receiptNumber: string | null;
  createdAt: string;
}

export interface TableStats {
  orderPointId: string;
  name: string;
  paidCard: number;
  paidCash: number;
  tipCard: number;
  tipCash: number;
  unpaid: number;
  settled: number;
  /** true when this is a protocol order point. */
  protocol: boolean;
}

export interface PlacedOrder {
  id: string;
  createdBy: string | null;
  createdAt: string;
  status: string;
  items: PlacedOrderItem[];
  total: number;
}

/** An undelivered order across the waiter's tables (includes the order point name). */
export interface ActiveOrder {
  id: string;
  orderPointId: string;
  orderPointName: string;
  createdBy: string | null;
  createdAt: string;
  status: 'ORDERED' | 'READY';
  items: PlacedOrderItem[];
  total: number;
}

export interface PlaceOrderItem {
  menuItemId: string | null;
  name: string;
  price: number | null;
  quantity: number;
}
