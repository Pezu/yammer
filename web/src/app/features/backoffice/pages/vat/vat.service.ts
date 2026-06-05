import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface VatType {
  id: string;
  value: number;
}

export interface VatTypeInput {
  value: number;
}

@Injectable({ providedIn: 'root' })
export class VatService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/vat-types`;

  list(): Observable<VatType[]> {
    return this.http.get<VatType[]>(this.baseUrl);
  }

  create(input: VatTypeInput): Observable<VatType> {
    return this.http.post<VatType>(this.baseUrl, input);
  }

  update(id: string, input: VatTypeInput): Observable<VatType> {
    return this.http.put<VatType>(`${this.baseUrl}/${id}`, input);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
