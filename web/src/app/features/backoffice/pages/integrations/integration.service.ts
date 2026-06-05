import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export type IntegrationType = 'CASH_REGISTER' | 'PRINTER';

export interface Integration {
  id: string;
  locationId: string;
  name: string;
  ip: string | null;
  type: IntegrationType;
}

export interface IntegrationInput {
  locationId: string;
  name: string;
  ip: string | null;
  type: IntegrationType;
}

@Injectable({ providedIn: 'root' })
export class IntegrationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/integrations`;

  list(locationId: string, type?: IntegrationType): Observable<Integration[]> {
    let params = new HttpParams().set('locationId', locationId);
    if (type) {
      params = params.set('type', type);
    }
    return this.http.get<Integration[]>(this.baseUrl, { params });
  }

  create(input: IntegrationInput): Observable<Integration> {
    return this.http.post<Integration>(this.baseUrl, input);
  }

  update(id: string, input: IntegrationInput): Observable<Integration> {
    return this.http.put<Integration>(`${this.baseUrl}/${id}`, input);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
