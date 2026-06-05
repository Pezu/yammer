import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Location {
  id: string;
  name: string;
  clientId: string;
}

export interface LocationInput {
  name: string;
  clientId: string | null;
}

@Injectable({ providedIn: 'root' })
export class LocationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/locations`;

  list(clientId?: string): Observable<Location[]> {
    const params = clientId ? new HttpParams().set('clientId', clientId) : undefined;
    return this.http.get<Location[]>(this.baseUrl, { params });
  }

  create(location: LocationInput): Observable<Location> {
    return this.http.post<Location>(this.baseUrl, location);
  }

  update(id: string, location: LocationInput): Observable<Location> {
    return this.http.put<Location>(`${this.baseUrl}/${id}`, location);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
