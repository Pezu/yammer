import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Menu {
  id: string;
  locationId: string;
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

  listMenus(locationId: string): Observable<Menu[]> {
    const params = new HttpParams().set('locationId', locationId);
    return this.http.get<Menu[]>(`${this.baseUrl}/menus`, { params });
  }

  createMenu(locationId: string, name: string): Observable<Menu> {
    return this.http.post<Menu>(`${this.baseUrl}/menus`, { locationId, name });
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
