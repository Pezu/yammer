import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../core/auth.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService } from '../locations/location.service';
import { User, UserService } from '../users/user.service';
import { Event, EventService } from '../events/event.service';
import { OrderPointAssignmentService, ParentAssignment } from './order-point-assignment.service';

@Component({
  selector: 'app-assign-page',
  imports: [],
  templateUrl: './assign-page.html',
  styleUrl: './assign-page.scss',
})
export class AssignPage {
  private readonly auth = inject(AuthService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly userService = inject(UserService);
  private readonly eventService = inject(EventService);
  private readonly assignmentService = inject(OrderPointAssignmentService);

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly error = signal<string | null>(null);
  readonly loading = signal(false);

  // --- client combo (SUPER only) ---
  readonly clients = signal<Client[]>([]);
  readonly clientId = signal<string>('');
  readonly clientComboOpen = signal(false);
  readonly clientSearch = signal('');
  readonly clientOptions = computed(() => {
    const q = this.clientSearch().trim().toLowerCase();
    return (q ? this.clients().filter((c) => c.name.toLowerCase().includes(q)) : this.clients()).slice(0, 5);
  });
  readonly clientName = computed(
    () => this.clients().find((c) => c.id === this.clientId())?.name ?? 'Select a client…',
  );
  readonly clientChosen = computed(() => (this.isSuper() ? !!this.clientId() : true));

  // --- location combo ---
  readonly locations = signal<Location[]>([]);
  readonly locationId = signal<string>('');
  readonly locationComboOpen = signal(false);
  readonly locationSearch = signal('');
  readonly locationOptions = computed(() => {
    const q = this.locationSearch().trim().toLowerCase();
    return (q ? this.locations().filter((l) => l.name.toLowerCase().includes(q)) : this.locations()).slice(0, 5);
  });
  readonly locationName = computed(
    () => this.locations().find((l) => l.id === this.locationId())?.name ?? 'Select a location…',
  );

  // --- event combo ---
  readonly events = signal<Event[]>([]);
  readonly eventId = signal<string>('');
  readonly eventComboOpen = signal(false);
  readonly eventSearch = signal('');
  readonly eventOptions = computed(() => {
    const q = this.eventSearch().trim().toLowerCase();
    return (q ? this.events().filter((e) => e.name.toLowerCase().includes(q)) : this.events()).slice(0, 5);
  });
  readonly eventName = computed(
    () => this.events().find((e) => e.id === this.eventId())?.name ?? 'Select an event…',
  );

  private currentClientId(): string {
    return this.locations().find((l) => l.id === this.locationId())?.clientId ?? this.ownClientId();
  }

  // --- users of the location's client ---
  private readonly allUsers = signal<User[]>([]);
  readonly users = computed(() => {
    const cid = this.currentClientId();
    return this.allUsers()
      .filter((u) => u.clientId === cid)
      .sort((a, b) => a.username.localeCompare(b.username));
  });
  userName(id: string): string {
    return this.allUsers().find((u) => u.id === id)?.username ?? id;
  }

  // --- parent rows + per-row multi-select combo ---
  readonly rows = signal<ParentAssignment[]>([]);
  readonly openParent = signal<string | null>(null);

  toggleCombo(parent: string): void {
    this.openParent.update((p) => (p === parent ? null : parent));
  }
  closeCombo(): void {
    this.openParent.set(null);
  }
  isAssigned(row: ParentAssignment, userId: string): boolean {
    return row.userIds.includes(userId);
  }
  assignedLabel(row: ParentAssignment): string {
    if (!row.userIds.length) {
      return 'Unassigned';
    }
    return row.userIds.map((id) => this.userName(id)).join(', ');
  }

  toggleUser(row: ParentAssignment, userId: string): void {
    const next = row.userIds.includes(userId)
      ? row.userIds.filter((id) => id !== userId)
      : [...row.userIds, userId];
    // optimistic update
    this.rows.update((rows) =>
      rows.map((r) => (r.parentName === row.parentName ? { ...r, userIds: next } : r)),
    );
    this.error.set(null);
    this.assignmentService.set(this.locationId(), this.eventId(), row.parentName, next).subscribe({
      next: (saved) =>
        this.rows.update((rows) =>
          rows.map((r) => (r.parentName === saved.parentName ? saved : r)),
        ),
      error: (err: HttpErrorResponse) => {
        // revert on failure
        this.rows.update((rows) =>
          rows.map((r) => (r.parentName === row.parentName ? row : r)),
        );
        this.error.set(err.status === 400 ? 'Could not save assignment.' : 'Failed to save assignment.');
      },
    });
  }

  constructor() {
    if (this.isSuper()) {
      this.clientService.list().subscribe({
        next: (clients) => {
          this.clients.set(clients);
          if (clients.length === 1) {
            this.selectClient(clients[0].id);
          }
        },
        error: () => this.error.set('Failed to load clients.'),
      });
    } else {
      this.loadLocations(this.ownClientId());
    }
    // users are scoped server-side; load once and filter by the chosen location's client
    this.userService.list().subscribe({
      next: (users) => this.allUsers.set(users),
      error: () => this.error.set('Failed to load users.'),
    });
  }

  // --- client combo ---
  toggleClientCombo(): void {
    this.clientSearch.set('');
    this.clientComboOpen.update((o) => !o);
  }
  closeClientCombo(): void {
    this.clientComboOpen.set(false);
  }
  selectClient(id: string): void {
    this.clientId.set(id);
    this.clientComboOpen.set(false);
    this.resetLocation();
    this.loadLocations(id);
  }
  private loadLocations(clientId: string): void {
    if (!clientId) {
      this.locations.set([]);
      return;
    }
    this.locationService.list(clientId).subscribe({
      next: (locations) => {
        this.locations.set(locations);
        if (locations.length === 1) {
          this.selectLocation(locations[0].id);
        }
      },
      error: () => this.error.set('Failed to load locations.'),
    });
  }
  private resetLocation(): void {
    this.locationId.set('');
    this.locations.set([]);
    this.resetEvent();
  }
  private resetEvent(): void {
    this.eventId.set('');
    this.events.set([]);
    this.rows.set([]);
    this.openParent.set(null);
  }

  // --- location combo ---
  toggleLocationCombo(): void {
    this.locationSearch.set('');
    this.locationComboOpen.update((o) => !o);
  }
  closeLocationCombo(): void {
    this.locationComboOpen.set(false);
  }
  selectLocation(id: string): void {
    this.locationId.set(id);
    this.locationComboOpen.set(false);
    this.resetEvent();
    this.loadEvents(id);
  }
  private loadEvents(locationId: string): void {
    this.eventService.list(locationId).subscribe({
      next: (events) => {
        this.events.set(events);
        if (events.length === 1) {
          this.selectEvent(events[0].id);
        }
      },
      error: () => this.error.set('Failed to load events.'),
    });
  }

  // --- event combo ---
  toggleEventCombo(): void {
    this.eventSearch.set('');
    this.eventComboOpen.update((o) => !o);
  }
  closeEventCombo(): void {
    this.eventComboOpen.set(false);
  }
  selectEvent(id: string): void {
    this.eventId.set(id);
    this.eventComboOpen.set(false);
    this.openParent.set(null);
    this.loadRows();
  }
  private loadRows(): void {
    if (!this.locationId() || !this.eventId()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.assignmentService.list(this.locationId(), this.eventId()).subscribe({
      next: (rows) => {
        this.rows.set(this.sortParents(rows));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load order points.');
        this.loading.set(false);
      },
    });
  }
  private sortParents(rows: ParentAssignment[]): ParentAssignment[] {
    return [...rows].sort((a, b) =>
      a.parentName.localeCompare(b.parentName, undefined, { numeric: true }),
    );
  }
}
