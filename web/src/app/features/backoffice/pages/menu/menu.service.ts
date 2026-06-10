import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Menu {
  id: string;
  locationId: string;
  eventId: string | null;
  name: string;
}

/** One node of a menu tree. Category: orderable=false. Product: orderable=true + price. */
export interface MenuNode {
  id?: string | null;
  name: string;
  orderable: boolean;
  price: number | null;
  vatTypeId: string | null;
  children: MenuNode[];
}

@Injectable({ providedIn: 'root' })
export class MenuService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/menu`;

  listMenus(locationId: string, eventId: string): Observable<Menu[]> {
    let params = new HttpParams().set('locationId', locationId);
    if (eventId) {
      params = params.set('eventId', eventId);
    }
    return this.http.get<Menu[]>(`${this.baseUrl}/menus`, { params });
  }

  createMenu(locationId: string, eventId: string, name: string): Observable<Menu> {
    return this.http.post<Menu>(`${this.baseUrl}/menus`, { locationId, eventId: eventId || null, name });
  }

  deleteMenu(menuId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/menus/${menuId}`);
  }

  getTree(menuId: string): Observable<MenuNode[]> {
    return this.http.get<MenuNode[]>(`${this.baseUrl}/menus/${menuId}/tree`);
  }

  saveTree(menuId: string, nodes: MenuNode[]): Observable<MenuNode[]> {
    return this.http.put<MenuNode[]>(`${this.baseUrl}/menus/${menuId}/tree`, nodes);
  }
}
