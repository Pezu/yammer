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
  orderPointId: string;
  orderPointName: string;
  createdBy: string | null;
  createdAt: string;
  status: string;
  items: OrderItem[];
  total: number;
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

@Injectable({ providedIn: 'root' })
export class OrderReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/orders`;

  list(): Observable<Order[]> {
    return this.http.get<Order[]>(this.baseUrl);
  }

  products(): Observable<ProductReportRow[]> {
    return this.http.get<ProductReportRow[]>(`${this.baseUrl}/products-report`);
  }

  sales(): Observable<SalesIntervalRow[]> {
    return this.http.get<SalesIntervalRow[]>(`${this.baseUrl}/sales-report`);
  }
}
