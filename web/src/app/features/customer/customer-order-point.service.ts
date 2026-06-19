import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** One node of the menu tree: a category (orderable=false, has children) or a product. */
export interface MenuNode {
  id: string;
  name: string;
  orderable: boolean;
  price: number | null;
  vatTypeId: string | null;
  children: MenuNode[];
}

/** Public, customer-facing view of an order point (resolved from the QR's order-point id). */
export interface CustomerOrderPoint {
  id: string;
  name: string;
  eventId: string | null;
  eventName: string | null;
  menu: MenuNode[];
}

@Injectable({ providedIn: 'root' })
export class CustomerOrderPointService {
  private readonly http = inject(HttpClient);

  /** Public endpoint — no auth; the event is embedded in (derived from) the order point. */
  getOrderPoint(opId: string): Observable<CustomerOrderPoint> {
    return this.http.get<CustomerOrderPoint>(`${environment.apiUrl}/public/order-points/${opId}`);
  }

  /** Place a self-service order (pay-later). Only product ids + quantities are sent. */
  placeOrder(opId: string, items: { menuItemId: string; quantity: number }[]): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/public/order-points/${opId}/orders`, { items });
  }
}
