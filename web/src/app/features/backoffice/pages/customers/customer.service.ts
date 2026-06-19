import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface PagedResponse<T> {
  content: T[];
  total: number;
  page: number;
  size: number;
}

export interface Customer {
  id: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  email: string | null;
}

export interface CustomerInput {
  firstName: string;
  lastName: string;
  phone: string | null;
  email: string | null;
}

export interface CustomerImportResult {
  imported: number;
  skipped: number;
  errors: string[];
}

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/customers`;

  list(): Observable<Customer[]> {
    return this.http.get<Customer[]>(this.baseUrl);
  }

  /** One page of customers (ordered by last then first name), paginated on the server. */
  listPaged(page: number, size: number): Observable<PagedResponse<Customer>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<Customer>>(`${this.baseUrl}/page`, { params });
  }

  create(input: CustomerInput): Observable<Customer> {
    return this.http.post<Customer>(this.baseUrl, input);
  }

  update(id: string, input: CustomerInput): Observable<Customer> {
    return this.http.put<Customer>(`${this.baseUrl}/${id}`, input);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  importXlsx(file: File): Observable<CustomerImportResult> {
    const data = new FormData();
    data.append('file', file);
    return this.http.post<CustomerImportResult>(`${this.baseUrl}/import`, data);
  }
}
