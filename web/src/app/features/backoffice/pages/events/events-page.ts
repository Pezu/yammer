import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../core/auth.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService } from '../locations/location.service';
import { Event, EventService } from './event.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';
import { DatePicker } from '../../../../shared/date-picker/date-picker';

@Component({
  selector: 'app-events-page',
  imports: [ReactiveFormsModule, ConfirmDialog, DatePicker],
  templateUrl: './events-page.html',
  styleUrl: './events-page.scss',
})
export class EventsPage {
  private readonly auth = inject(AuthService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly eventService = inject(EventService);

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly error = signal<string | null>(null);
  readonly loading = signal(false);
  readonly exportingId = signal<string | null>(null);

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

  // --- events table ---
  readonly events = signal<Event[]>([]);
  readonly pendingDelete = signal<Event | null>(null);

  // --- create / edit modal ---
  readonly modalOpen = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly saving = signal(false);
  readonly formName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly formStartDate = signal<string>('');
  readonly formEndDate = signal<string>('');

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
    this.events.set([]);
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
    this.error.set(null);
    this.loadEvents(id);
  }
  private loadEvents(locationId: string): void {
    this.loading.set(true);
    this.eventService.list(locationId).subscribe({
      next: (events) => {
        this.events.set(events);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load events.');
        this.loading.set(false);
      },
    });
  }

  // --- create / edit ---
  startCreate(): void {
    this.editingId.set(null);
    this.formName.reset();
    this.formStartDate.set('');
    this.formEndDate.set('');
    this.error.set(null);
    this.modalOpen.set(true);
  }
  startEdit(ev: Event): void {
    this.editingId.set(ev.id);
    this.formName.setValue(ev.name);
    this.formStartDate.set(ev.startDate);
    this.formEndDate.set(ev.endDate);
    this.error.set(null);
    this.modalOpen.set(true);
  }
  closeModal(): void {
    this.modalOpen.set(false);
  }
  save(): void {
    if (this.formName.invalid || !this.formStartDate() || !this.formEndDate() || !this.locationId()) {
      return;
    }
    if (this.formEndDate() < this.formStartDate()) {
      this.error.set('End date cannot be before start date.');
      return;
    }
    const input = {
      locationId: this.locationId(),
      name: this.formName.value.trim(),
      startDate: this.formStartDate(),
      endDate: this.formEndDate(),
    };
    this.saving.set(true);
    const id = this.editingId();
    const request$ = id ? this.eventService.update(id, input) : this.eventService.create(input);
    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.loadEvents(this.locationId());
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(err.error?.message ?? 'Failed to save event.');
      },
    });
  }

  // --- delete ---
  exportQr(ev: Event): void {
    if (this.exportingId()) {
      return;
    }
    this.exportingId.set(ev.id);
    this.error.set(null);
    this.eventService.exportQrPdf(ev.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `qr-${ev.name}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.exportingId.set(null);
      },
      error: () => {
        this.error.set('Failed to export QR codes.');
        this.exportingId.set(null);
      },
    });
  }

  askDelete(ev: Event): void {
    this.pendingDelete.set(ev);
  }
  cancelDelete(): void {
    this.pendingDelete.set(null);
  }
  confirmDelete(): void {
    const ev = this.pendingDelete();
    if (!ev) {
      return;
    }
    this.eventService.delete(ev.id).subscribe({
      next: () => {
        this.pendingDelete.set(null);
        this.loadEvents(this.locationId());
      },
      error: () => {
        this.pendingDelete.set(null);
        this.error.set('Failed to delete event.');
      },
    });
  }
}
