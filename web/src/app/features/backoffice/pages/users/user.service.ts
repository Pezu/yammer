import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface User {
  id: string;
  username: string;
  name: string | null;
  phone: string | null;
  email: string | null;
  roles: string[];
  clientId: string | null;
}

export interface UserInput {
  username: string;
  name: string;
  password: string;
  phone: string;
  email: string;
  roles: string[];
  clientId: string | null;
}

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/users`;

  list(): Observable<User[]> {
    return this.http.get<User[]>(this.baseUrl);
  }

  create(user: UserInput): Observable<User> {
    return this.http.post<User>(this.baseUrl, user);
  }

  update(id: string, user: UserInput): Observable<User> {
    return this.http.put<User>(`${this.baseUrl}/${id}`, user);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
