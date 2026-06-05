import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Role {
  id: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class RoleService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/roles`;

  list(): Observable<Role[]> {
    return this.http.get<Role[]>(this.baseUrl);
  }

  create(role: string): Observable<Role> {
    return this.http.post<Role>(this.baseUrl, { role });
  }

  update(id: string, role: string): Observable<Role> {
    return this.http.put<Role>(`${this.baseUrl}/${id}`, { role });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
