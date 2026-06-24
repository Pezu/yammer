import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type OrderStatus = 'ORDERED' | 'READY' | 'DELIVERED' | 'CANCELED';

export interface ServiceOrderItem {
  id: string;
  menuItemId: string | null;
  name: string;
  price: number | null;
  quantity: number;
  paid: boolean;
}

export interface ServiceOrder {
  id: string;
  orderNo: number;
  orderPointId: string;
  orderPointName: string;
  createdBy: string | null;
  createdAt: string;
  status: OrderStatus;
  items: ServiceOrderItem[];
  total: number;
}

@Injectable({ providedIn: 'root' })
export class ServiceOrderService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/orders`;

  /** Orders routed to the service user's assigned service points (ORDERED / READY). */
  board(): Observable<ServiceOrder[]> {
    return this.http.get<ServiceOrder[]>(
      `${environment.apiUrl}/order-point-assignments/service-board`,
    );
  }

  /** Move an order to a new status. */
  updateStatus(orderId: string, status: OrderStatus): Observable<ServiceOrder> {
    return this.http.patch<ServiceOrder>(`${this.baseUrl}/${orderId}/status`, { status });
  }
}
