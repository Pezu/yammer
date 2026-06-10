import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

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
  createdBy: string | null;
  createdAt: string;
  status: string;
  items: OrderItem[];
  total: number;
  paid: 'NOT' | 'PAR' | 'PAID';
}

export interface ProductReportRow {
  name: string;
  quantity: number;
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
}

export interface WaiterTableRow {
  waiter: string;
  table: string;
  ordered: number;
  paid: number;
  tip: number;
  protocol: number;
}

@Injectable({ providedIn: 'root' })
export class OrderReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/orders`;

  list(): Observable<Order[]> {
    return this.http.get<Order[]>(this.baseUrl);
  }

  /** Update an order's unpaid item quantities (quantity ≤ 0 deletes the item). */
  updateItems(orderId: string, items: { id: string; quantity: number }[]): Observable<Order> {
    return this.http.patch<Order>(`${this.baseUrl}/${orderId}/items`, { items });
  }

  /** Delete an order entirely (only allowed when nothing is paid). */
  deleteOrder(orderId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${orderId}`);
  }

  products(): Observable<ProductReportRow[]> {
    return this.http.get<ProductReportRow[]>(`${this.baseUrl}/products-report`);
  }

  sales(): Observable<SalesIntervalRow[]> {
    return this.http.get<SalesIntervalRow[]>(`${this.baseUrl}/sales-report`);
  }

  salesSummary(): Observable<SalesSummary> {
    return this.http.get<SalesSummary>(`${this.baseUrl}/sales-summary`);
  }

  tablesReport(): Observable<TableReportRow[]> {
    return this.http.get<TableReportRow[]>(`${this.baseUrl}/tables-report`);
  }

  waitersReport(): Observable<WaiterReportRow[]> {
    return this.http.get<WaiterReportRow[]>(`${this.baseUrl}/waiters-report`);
  }

  waiterTablesReport(): Observable<WaiterTableRow[]> {
    return this.http.get<WaiterTableRow[]>(`${this.baseUrl}/waiter-tables-report`);
  }
}
