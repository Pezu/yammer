import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

/** A "combined" product across a location's menus. */
export interface RecipeItem {
  id: string;
  name: string;
  menuId: string;
  menuName: string;
}

/** One component row of a combined product's recipe. */
export interface RecipeComponent {
  id: string;
  name: string;
  quantity: number | null;
  unit: string | null;
  percentage: number | null;
}

export type RecipeComponentInput = Omit<RecipeComponent, 'id'>;

@Injectable({ providedIn: 'root' })
export class RecipeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/recipes`;

  list(locationId: string, eventId: string): Observable<RecipeItem[]> {
    let params = new HttpParams().set('locationId', locationId);
    if (eventId) {
      params = params.set('eventId', eventId);
    }
    return this.http.get<RecipeItem[]>(this.baseUrl, { params });
  }

  listComponents(menuItemId: string): Observable<RecipeComponent[]> {
    const params = new HttpParams().set('menuItemId', menuItemId);
    return this.http.get<RecipeComponent[]>(`${this.baseUrl}/components`, { params });
  }

  createComponent(menuItemId: string, body: RecipeComponentInput): Observable<RecipeComponent> {
    const params = new HttpParams().set('menuItemId', menuItemId);
    return this.http.post<RecipeComponent>(`${this.baseUrl}/components`, body, { params });
  }

  updateComponent(id: string, body: RecipeComponentInput): Observable<RecipeComponent> {
    return this.http.put<RecipeComponent>(`${this.baseUrl}/components/${id}`, body);
  }

  deleteComponent(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/components/${id}`);
  }
}
