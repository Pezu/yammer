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
  /** When false, ordering goes through online (Netopia) payment. */
  payLater: boolean;
  menu: MenuNode[];
}

/** Customer identity for the pay-now flow: a returning customer's id, or first-time details. */
export interface CustomerInfo {
  id?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  prefix?: string;
  phone?: string;
}

/** Result of looking a customer up by prefix + phone. */
export interface CustomerLookupResult {
  customerId: string | null;
}

/**
 * Result of placing an order. Pay-later → `orderId` set (order created). Pay-now → `paymentUrl`
 * set (redirect the customer to the gateway) and `reference` to poll for the result.
 */
export interface PlaceOrderResult {
  orderId: string | null;
  paymentUrl: string | null;
  reference: string | null;
  /** Resolved customer id (pay-now) — the client stores it to skip the info form next time. */
  customerId: string | null;
}

/** Online-payment intent status, polled by the payment-return page. */
export interface OnlinePaymentStatus {
  status: 'PENDING' | 'PAID' | 'FAILED' | 'EXPIRED';
  orderId: string | null;
}

/** A customer's past order (self-service order-history view). */
export interface CustomerOrder {
  id: string;
  orderNo: number;
  status: string;
  createdAt: string;
  total: number;
  items: { name: string; quantity: number; price: number }[];
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
    customer?: CustomerInfo,
  ): Observable<PlaceOrderResult> {
    return this.http.post<PlaceOrderResult>(
      `${environment.apiUrl}/public/order-points/${opId}/orders`,
      { items, returnUrl, customer },
    );
  }

  /** Look up a customer by dial prefix + phone (pay-now flow). `customerId` is null if not found. */
  lookupCustomer(prefix: string, phone: string): Observable<CustomerLookupResult> {
    return this.http.post<CustomerLookupResult>(
      `${environment.apiUrl}/public/customers/lookup`,
      { prefix, phone },
    );
  }

  /** A customer's order history at one event (the "Orders" drawer view). */
  customerOrders(customerId: string, eventId: string): Observable<CustomerOrder[]> {
    return this.http.get<CustomerOrder[]>(
      `${environment.apiUrl}/public/customers/${customerId}/orders`,
      { params: { eventId } },
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
