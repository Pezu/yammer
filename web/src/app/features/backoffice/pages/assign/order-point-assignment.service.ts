import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface ParentAssignment {
  parentName: string;
  userIds: string[];
}

@Injectable({ providedIn: 'root' })
export class OrderPointAssignmentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/order-point-assignments`;

  list(locationId: string, eventId: string): Observable<ParentAssignment[]> {
    let params = new HttpParams().set('locationId', locationId);
    if (eventId) {
      params = params.set('eventId', eventId);
    }
    return this.http.get<ParentAssignment[]>(this.baseUrl, { params });
  }

  set(
    locationId: string,
    eventId: string,
    parentName: string,
    userIds: string[],
  ): Observable<ParentAssignment> {
    return this.http.put<ParentAssignment>(this.baseUrl, {
      locationId,
      eventId: eventId || null,
      parentName,
      userIds,
    });
  }
}
