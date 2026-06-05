import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../core/auth.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService } from '../locations/location.service';
import { Integration, IntegrationService, IntegrationType } from './integration.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-integrations-page',
  imports: [ReactiveFormsModule, ConfirmDialog],
  templateUrl: './integrations-page.html',
  styleUrl: './integrations-page.scss',
})
export class IntegrationsPage {
  private readonly auth = inject(AuthService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly integrationService = inject(IntegrationService);

  // --- device type (custom combo, per row) ---
  readonly typeOptions: { value: IntegrationType; label: string }[] = [
    { value: 'CASH_REGISTER', label: 'Cash Register' },
    { value: 'PRINTER', label: 'Printer' },
  ];
  typeLabel(t: IntegrationType): string {
    return this.typeOptions.find((o) => o.value === t)?.label ?? '';
  }

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

  // --- table ---
  readonly items = signal<Integration[]>([]);
  readonly draft = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly pendingDelete = signal<Integration | null>(null);

  readonly draftName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly draftIp = new FormControl('', { nonNullable: true });
  readonly draftType = signal<IntegrationType>('CASH_REGISTER');
  readonly draftTypeComboOpen = signal(false);
  readonly editName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly editIp = new FormControl('', { nonNullable: true });
  readonly editType = signal<IntegrationType>('CASH_REGISTER');
  readonly editTypeComboOpen = signal(false);

  // per-row type combos
  toggleDraftTypeCombo(): void {
    this.draftTypeComboOpen.update((o) => !o);
  }
  closeDraftTypeCombo(): void {
    this.draftTypeComboOpen.set(false);
  }
  selectDraftType(t: IntegrationType): void {
    this.draftType.set(t);
    this.draftTypeComboOpen.set(false);
  }
  toggleEditTypeCombo(): void {
    this.editTypeComboOpen.update((o) => !o);
  }
  closeEditTypeCombo(): void {
    this.editTypeComboOpen.set(false);
  }
  selectEditType(t: IntegrationType): void {
    this.editType.set(t);
    this.editTypeComboOpen.set(false);
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
    this.resetTable();
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
    this.resetTable();
    this.loadItems(id);
  }
  private resetTable(): void {
    this.items.set([]);
    this.draft.set(false);
    this.editingId.set(null);
    this.error.set(null);
  }
  private loadItems(locationId: string): void {
    this.loading.set(true);
    this.integrationService.list(locationId).subscribe({
      next: (rows) => {
        this.items.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load integrations.');
        this.loading.set(false);
      },
    });
  }

  // --- create ---
  startCreate(): void {
    this.editingId.set(null);
    this.draftName.reset();
    this.draftIp.reset();
    this.draftType.set('CASH_REGISTER');
    this.draftTypeComboOpen.set(false);
    this.error.set(null);
    this.draft.set(true);
  }
  cancelCreate(): void {
    this.draftTypeComboOpen.set(false);
    this.draft.set(false);
  }
  saveCreate(): void {
    if (this.draftName.invalid || !this.locationId()) {
      return;
    }
    this.integrationService
      .create({
        locationId: this.locationId(),
        name: this.draftName.value.trim(),
        ip: this.draftIp.value.trim() || null,
        type: this.draftType(),
      })
      .subscribe({
        next: (item) => {
          this.items.update((list) => this.sorted([...list, item]));
          this.draft.set(false);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
      });
  }

  // --- edit ---
  startEdit(item: Integration): void {
    this.draft.set(false);
    this.editingId.set(item.id);
    this.editName.setValue(item.name);
    this.editIp.setValue(item.ip ?? '');
    this.editType.set(item.type);
    this.editTypeComboOpen.set(false);
    this.error.set(null);
  }
  cancelEdit(): void {
    this.editTypeComboOpen.set(false);
    this.editingId.set(null);
  }
  saveEdit(item: Integration): void {
    if (this.editName.invalid) {
      return;
    }
    this.integrationService
      .update(item.id, {
        locationId: item.locationId,
        name: this.editName.value.trim(),
        ip: this.editIp.value.trim() || null,
        type: this.editType(),
      })
      .subscribe({
        next: (updated) => {
          this.items.update((list) => this.sorted(list.map((i) => (i.id === updated.id ? updated : i))));
          this.editingId.set(null);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
      });
  }

  // --- delete ---
  remove(item: Integration): void {
    this.error.set(null);
    this.pendingDelete.set(item);
  }
  cancelDelete(): void {
    this.pendingDelete.set(null);
  }
  confirmDelete(): void {
    const item = this.pendingDelete();
    if (!item) {
      return;
    }
    this.pendingDelete.set(null);
    this.integrationService.delete(item.id).subscribe({
      next: () => this.items.update((list) => list.filter((i) => i.id !== item.id)),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  private sorted(list: Integration[]): Integration[] {
    return [...list].sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 400) {
      return 'Please check the fields.';
    }
    return `Failed to ${action} integration.`;
  }
}
