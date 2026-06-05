import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Location, LocationService } from './location.service';
import { Client, ClientService } from '../clients/client.service';
import { AuthService } from '../../../../core/auth.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-locations-page',
  imports: [ReactiveFormsModule, ConfirmDialog],
  templateUrl: './locations-page.html',
  styleUrl: './locations-page.scss',
})
export class LocationsPage {
  private readonly locationService = inject(LocationService);
  private readonly clientService = inject(ClientService);
  private readonly auth = inject(AuthService);

  readonly locations = signal<Location[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly draft = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly pendingDelete = signal<Location | null>(null);

  readonly draftName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly editName = new FormControl('', { nonNullable: true, validators: [Validators.required] });

  // --- access model (same as Users) ---
  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  /** SUPER-only client selector ('' = none chosen yet). */
  readonly clientFilter = signal<string>('');
  readonly comboOpen = signal(false);
  readonly comboSearch = signal('');
  readonly comboOptions = computed(() => {
    const q = this.comboSearch().trim().toLowerCase();
    const matches = q
      ? this.clients().filter((c) => c.name.toLowerCase().includes(q))
      : this.clients();
    return matches.slice(0, 5);
  });
  readonly selectedClientName = computed(
    () => this.clients().find((c) => c.id === this.clientFilter())?.name ?? 'Select a client…',
  );

  /** SUPER must pick a client before the table shows; others always see it. */
  readonly showTable = computed(() => !this.isSuper() || !!this.clientFilter());
  readonly visibleLocations = computed(() => {
    const filter = this.clientFilter();
    return filter ? this.locations().filter((l) => l.clientId === filter) : this.locations();
  });

  constructor() {
    this.load();
    this.clientService.list().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        if (this.isSuper() && clients.length === 1 && !this.clientFilter()) {
          this.clientFilter.set(clients[0].id);
        }
      },
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.locationService.list().subscribe({
      next: (locations) => {
        this.locations.set(locations);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load locations.');
        this.loading.set(false);
      },
    });
  }

  // --- client combo ---

  toggleCombo(): void {
    this.comboSearch.set('');
    this.comboOpen.update((open) => !open);
  }

  closeCombo(): void {
    this.comboOpen.set(false);
  }

  selectClient(id: string): void {
    this.clientFilter.set(id);
    this.comboOpen.set(false);
  }

  // --- create ---

  startCreate(): void {
    this.editingId.set(null);
    this.draftName.reset();
    this.error.set(null);
    this.draft.set(true);
  }

  cancelCreate(): void {
    this.draft.set(false);
  }

  saveCreate(): void {
    if (this.draftName.invalid) {
      return;
    }
    const clientId = (this.isSuper() ? this.clientFilter() : this.ownClientId()) || null;
    this.locationService.create({ name: this.draftName.value.trim(), clientId }).subscribe({
      next: (location) => {
        this.locations.update((list) => this.sorted([...list, location]));
        this.draft.set(false);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
    });
  }

  // --- edit ---

  startEdit(location: Location): void {
    this.draft.set(false);
    this.editingId.set(location.id);
    this.editName.setValue(location.name);
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(location: Location): void {
    if (this.editName.invalid) {
      return;
    }
    const clientId = (this.isSuper() ? location.clientId : this.ownClientId()) || null;
    this.locationService.update(location.id, { name: this.editName.value.trim(), clientId }).subscribe({
      next: (updated) => {
        this.locations.update((list) => this.sorted(list.map((l) => (l.id === updated.id ? updated : l))));
        this.editingId.set(null);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
    });
  }

  // --- delete ---

  remove(location: Location): void {
    this.error.set(null);
    this.pendingDelete.set(location);
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  confirmDelete(): void {
    const location = this.pendingDelete();
    if (!location) {
      return;
    }
    this.pendingDelete.set(null);
    this.locationService.delete(location.id).subscribe({
      next: () => this.locations.update((list) => list.filter((l) => l.id !== location.id)),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  private sorted(list: Location[]): Location[] {
    return [...list].sort((a, b) => a.name.localeCompare(b.name));
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 400) {
      return 'Please check the fields.';
    }
    return `Failed to ${action} location.`;
  }
}
