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
  imageObject: string | null;
  children: MenuNode[];
}

/** Public, customer-facing view of an order point (resolved from the QR's order-point id). */
export interface CustomerOrderPoint {
  id: string;
  name: string;
  eventId: string | null;
  eventName: string | null;
  clientId: string | null;
  menu: MenuNode[];
}

/**
 * Result of placing an order. Pay-later → `orderId` set (order created). Pay-now → `paymentUrl`
 * set (redirect the customer to the gateway) and `reference` to poll for the result.
 */
export interface PlaceOrderResult {
  orderId: string | null;
  paymentUrl: string | null;
  reference: string | null;
}

/** Online-payment intent status, polled by the payment-return page. */
export interface OnlinePaymentStatus {
  status: 'PENDING' | 'PAID' | 'FAILED' | 'EXPIRED';
  orderId: string | null;
}

@Injectable({ providedIn: 'root' })
export class CustomerOrderPointService {
  private readonly http = inject(HttpClient);

  /** Public endpoint — no auth; the event is embedded in (derived from) the order point. */
  getOrderPoint(opId: string): Observable<CustomerOrderPoint> {
    return this.http.get<CustomerOrderPoint>(`${environment.apiUrl}/public/order-points/${opId}`);
  }

  /**
   * Place a self-service order. Only product ids + quantities are sent (prices resolved server-side).
   * `returnUrl` is where the gateway redirects the browser after a pay-now payment.
   */
  placeOrder(
    opId: string,
    items: { menuItemId: string; quantity: number }[],
    returnUrl: string,
  ): Observable<PlaceOrderResult> {
    return this.http.post<PlaceOrderResult>(
      `${environment.apiUrl}/public/order-points/${opId}/orders`,
      { items, returnUrl },
    );
  }

  /** Poll the status of an online payment (after returning from the gateway). */
  paymentStatus(reference: string): Observable<OnlinePaymentStatus> {
    return this.http.get<OnlinePaymentStatus>(
      `${environment.apiUrl}/public/payments/${reference}/status`,
    );
  }

  /** Public URL serving a stored menu-item image. */
  imageUrl(object: string): string {
    return `${environment.apiUrl}/public/menu-image?object=${encodeURIComponent(object)}`;
  }

  /** Public URL serving the client's logo (used as the event/brand logo). */
  clientLogoUrl(clientId: string): string {
    return `${environment.apiUrl}/clients/${clientId}/logo`;
  }
}
