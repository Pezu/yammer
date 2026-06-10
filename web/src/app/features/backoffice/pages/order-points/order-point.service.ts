import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface OrderPoint {
  id: string;
  locationId: string;
  eventId: string | null;
  name: string;
  payLater: boolean;
  protocol: boolean;
  menuId: string | null;
  serviceOrderPointId: string | null;
  printerId: string | null;
  cashRegisterId: string | null;
}

export interface OrderPointInput {
  locationId: string;
  eventId: string | null;
  name: string;
  payLater: boolean;
  protocol: boolean;
  menuId: string | null;
  serviceOrderPointId: string | null;
  printerId: string | null;
  cashRegisterId: string | null;
}

export interface OrderPointBatchInput {
  locationId: string;
  eventId: string | null;
  count: number;
  payLater: boolean;
  menuId: string | null;
  serviceOrderPointId: string | null;
  printerId: string | null;
  cashRegisterId: string | null;
}

@Injectable({ providedIn: 'root' })
export class OrderPointService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/order-points`;

  list(locationId: string, eventId: string): Observable<OrderPoint[]> {
    let params = new HttpParams().set('locationId', locationId);
    if (eventId) {
      params = params.set('eventId', eventId);
    }
    return this.http.get<OrderPoint[]>(this.baseUrl, { params });
  }

  create(input: OrderPointInput): Observable<OrderPoint> {
    return this.http.post<OrderPoint>(this.baseUrl, input);
  }

  createBatch(input: OrderPointBatchInput): Observable<OrderPoint[]> {
    return this.http.post<OrderPoint[]>(`${this.baseUrl}/batch`, input);
  }

  update(id: string, input: OrderPointInput): Observable<OrderPoint> {
    return this.http.put<OrderPoint>(`${this.baseUrl}/${id}`, input);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  split(id: string): Observable<OrderPoint> {
    return this.http.post<OrderPoint>(`${this.baseUrl}/${id}/split`, {});
  }
}
