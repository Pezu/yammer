import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Client {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  hasLogo: boolean;
}

export interface ClientInput {
  name: string;
  phone: string;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class ClientService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/clients`;

  list(): Observable<Client[]> {
    return this.http.get<Client[]>(this.baseUrl);
  }

  create(client: ClientInput): Observable<Client> {
    return this.http.post<Client>(this.baseUrl, client);
  }

  update(id: string, client: ClientInput): Observable<Client> {
    return this.http.put<Client>(`${this.baseUrl}/${id}`, client);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  uploadLogo(id: string, file: File): Observable<Client> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<Client>(`${this.baseUrl}/${id}/logo`, form);
  }

  deleteLogo(id: string): Observable<Client> {
    return this.http.delete<Client>(`${this.baseUrl}/${id}/logo`);
  }

  /** Public URL of a client's logo image (cache-busted by `version`). */
  logoUrl(id: string, version: number): string {
    return `${this.baseUrl}/${id}/logo?v=${version}`;
  }
}
