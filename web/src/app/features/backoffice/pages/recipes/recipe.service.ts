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
}
