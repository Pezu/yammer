import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Event {
  id: string;
  clientId: string;
  locationId: string;
  name: string;
  startDate: string;
  endDate: string;
}

export interface EventInput {
  locationId: string;
  name: string;
  startDate: string;
  endDate: string;
}

@Injectable({ providedIn: 'root' })
export class EventService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/events`;

  list(locationId: string): Observable<Event[]> {
    const params = new HttpParams().set('locationId', locationId);
    return this.http.get<Event[]>(this.baseUrl, { params });
  }

  create(input: EventInput): Observable<Event> {
    return this.http.post<Event>(this.baseUrl, input);
  }

  update(id: string, input: EventInput): Observable<Event> {
    return this.http.put<Event>(`${this.baseUrl}/${id}`, input);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** Download a printable PDF of QR codes (one per order point) for the event. */
  exportQrPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/qr`, { responseType: 'blob' });
  }
}
