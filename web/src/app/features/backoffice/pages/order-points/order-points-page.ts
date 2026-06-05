import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../core/auth.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService } from '../locations/location.service';
import { Menu, MenuService } from '../menu/menu.service';
import { OrderPoint, OrderPointService } from './order-point.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-order-points-page',
  imports: [ReactiveFormsModule, ConfirmDialog],
  templateUrl: './order-points-page.html',
  styleUrl: './order-points-page.scss',
})
export class OrderPointsPage {
  private readonly auth = inject(AuthService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly menuService = inject(MenuService);
  private readonly orderPointService = inject(OrderPointService);

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

  // --- menus for the selected location (assignable to each order point) ---
  readonly menus = signal<Menu[]>([]);
  menuName(menuId: string | null): string {
    return this.menus().find((m) => m.id === menuId)?.name ?? '';
  }
  /** Trigger label for a menu picker: the menu name, or a "no menu" placeholder. */
  menuLabel(menuId: string): string {
    return this.menuName(menuId) || 'No menu';
  }

  // --- custom menu pickers (one open at a time per context; no native <select>) ---
  readonly draftMenuComboOpen = signal(false);
  readonly editMenuComboOpen = signal(false);
  readonly batchMenuComboOpen = signal(false);

  toggleDraftMenuCombo(): void {
    this.draftMenuComboOpen.update((o) => !o);
  }
  closeDraftMenuCombo(): void {
    this.draftMenuComboOpen.set(false);
  }
  selectDraftMenu(id: string): void {
    this.draftMenuId.set(id);
    this.draftMenuComboOpen.set(false);
  }

  toggleEditMenuCombo(): void {
    this.editMenuComboOpen.update((o) => !o);
  }
  closeEditMenuCombo(): void {
    this.editMenuComboOpen.set(false);
  }
  selectEditMenu(id: string): void {
    this.editMenuId.set(id);
    this.editMenuComboOpen.set(false);
  }

  toggleBatchMenuCombo(): void {
    this.batchMenuComboOpen.update((o) => !o);
  }
  closeBatchMenuCombo(): void {
    this.batchMenuComboOpen.set(false);
  }
  selectBatchMenu(id: string): void {
    this.batchMenuId.set(id);
    this.batchMenuComboOpen.set(false);
  }

  // --- service point (a pay-later point links to a non-pay-later point) ---
  /** Candidate service points = the non-pay-later order points at this location. */
  readonly serviceOptions = computed(() => this.orderPoints().filter((p) => !p.payLater));
  serviceName(id: string | null): string {
    return this.orderPoints().find((p) => p.id === id)?.name ?? '';
  }
  /** Trigger label for a service-point picker. */
  serviceLabel(id: string | null): string {
    return this.serviceName(id) || 'No service point';
  }

  // custom service-point pickers (no native <select>) — editable only in edit/draft mode
  readonly draftServiceComboOpen = signal(false);
  readonly editServiceComboOpen = signal(false);

  toggleDraftServiceCombo(): void {
    this.draftServiceComboOpen.update((o) => !o);
  }
  closeDraftServiceCombo(): void {
    this.draftServiceComboOpen.set(false);
  }
  selectDraftService(id: string): void {
    this.draftServiceOrderPointId.set(id);
    this.draftServiceComboOpen.set(false);
  }

  toggleEditServiceCombo(): void {
    this.editServiceComboOpen.update((o) => !o);
  }
  closeEditServiceCombo(): void {
    this.editServiceComboOpen.set(false);
  }
  selectEditService(id: string): void {
    this.editServiceOrderPointId.set(id);
    this.editServiceComboOpen.set(false);
  }

  // --- order points table ---
  readonly orderPoints = signal<OrderPoint[]>([]);

  readonly draft = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly pendingDelete = signal<OrderPoint | null>(null);

  readonly draftName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly draftPayLater = signal(false);
  readonly draftProtocol = signal(false);
  readonly draftMenuId = signal<string>('');
  readonly draftServiceOrderPointId = signal<string>('');
  readonly editName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly editPayLater = signal(false);
  readonly editProtocol = signal(false);
  readonly editMenuId = signal<string>('');
  readonly editServiceOrderPointId = signal<string>('');

  // --- "add multiple" modal ---
  readonly batchModalOpen = signal(false);
  readonly batchCount = signal<number>(1);
  readonly batchPayLater = signal(false);
  readonly batchMenuId = signal<string>('');
  readonly savingBatch = signal(false);

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
    this.loadMenus(id);
    this.loadOrderPoints(id);
  }
  private resetTable(): void {
    this.orderPoints.set([]);
    this.menus.set([]);
    this.draft.set(false);
    this.editingId.set(null);
    this.error.set(null);
  }
  private loadMenus(locationId: string): void {
    this.menuService.listMenus(locationId).subscribe({
      next: (menus) => this.menus.set(menus),
      error: () => this.error.set('Failed to load menus.'),
    });
  }
  private loadOrderPoints(locationId: string): void {
    this.loading.set(true);
    this.orderPointService.list(locationId).subscribe({
      next: (points) => {
        this.orderPoints.set(points);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load order points.');
        this.loading.set(false);
      },
    });
  }

  // --- single create ---
  startCreate(): void {
    this.editingId.set(null);
    this.draftName.reset();
    this.draftPayLater.set(false);
    this.draftProtocol.set(false);
    this.draftMenuId.set('');
    this.draftMenuComboOpen.set(false);
    this.draftServiceOrderPointId.set('');
    this.draftServiceComboOpen.set(false);
    this.error.set(null);
    this.draft.set(true);
  }
  cancelCreate(): void {
    this.draftMenuComboOpen.set(false);
    this.draftServiceComboOpen.set(false);
    this.draft.set(false);
  }
  saveCreate(): void {
    if (this.draftName.invalid || !this.locationId()) {
      return;
    }
    this.orderPointService
      .create({
        locationId: this.locationId(),
        name: this.draftName.value.trim(),
        payLater: this.draftPayLater(),
        protocol: this.draftProtocol(),
        menuId: this.draftMenuId() || null,
        serviceOrderPointId: this.draftPayLater() ? this.draftServiceOrderPointId() || null : null,
      })
      .subscribe({
        next: (point) => {
          this.orderPoints.update((list) => this.sorted([...list, point]));
          this.draft.set(false);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
      });
  }

  // --- batch create ---
  openBatchModal(): void {
    if (!this.locationId()) {
      return;
    }
    this.batchCount.set(1);
    this.batchPayLater.set(false);
    this.batchMenuId.set('');
    this.batchMenuComboOpen.set(false);
    this.error.set(null);
    this.batchModalOpen.set(true);
  }
  closeBatchModal(): void {
    this.batchMenuComboOpen.set(false);
    this.batchModalOpen.set(false);
    this.savingBatch.set(false);
  }
  saveBatch(): void {
    const count = Number(this.batchCount());
    if (!this.locationId() || !Number.isInteger(count) || count < 1) {
      return;
    }
    this.savingBatch.set(true);
    this.orderPointService
      .createBatch({
        locationId: this.locationId(),
        count,
        payLater: this.batchPayLater(),
        menuId: this.batchMenuId() || null,
      })
      .subscribe({
        next: (created) => {
          this.orderPoints.update((list) => this.sorted([...list, ...created]));
          this.closeBatchModal();
        },
        error: (err: HttpErrorResponse) => {
          this.savingBatch.set(false);
          this.error.set(this.message(err, 'create'));
        },
      });
  }

  // --- edit ---
  startEdit(point: OrderPoint): void {
    this.draft.set(false);
    this.editingId.set(point.id);
    this.editName.setValue(point.name);
    this.editPayLater.set(point.payLater);
    this.editProtocol.set(point.protocol);
    this.editMenuId.set(point.menuId ?? '');
    this.editMenuComboOpen.set(false);
    this.editServiceOrderPointId.set(point.serviceOrderPointId ?? '');
    this.editServiceComboOpen.set(false);
    this.error.set(null);
  }
  cancelEdit(): void {
    this.editMenuComboOpen.set(false);
    this.editServiceComboOpen.set(false);
    this.editingId.set(null);
  }
  saveEdit(point: OrderPoint): void {
    if (this.editName.invalid) {
      return;
    }
    this.orderPointService
      .update(point.id, {
        locationId: point.locationId,
        name: this.editName.value.trim(),
        payLater: this.editPayLater(),
        protocol: this.editProtocol(),
        menuId: this.editMenuId() || null,
        serviceOrderPointId: this.editPayLater() ? this.editServiceOrderPointId() || null : null,
      })
      .subscribe({
        next: (updated) => {
          this.orderPoints.update((list) => this.sorted(list.map((p) => (p.id === updated.id ? updated : p))));
          this.editingId.set(null);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
      });
  }

  // --- split (pay-later points only) ---
  split(point: OrderPoint): void {
    if (!point.payLater) {
      return;
    }
    this.error.set(null);
    this.orderPointService.split(point.id).subscribe({
      next: (created) => this.orderPoints.update((list) => this.sorted([...list, created])),
      error: (err: HttpErrorResponse) =>
        this.error.set(
          err.status === 400
            ? 'Only pay-later points named like M1.1 can be split.'
            : 'Failed to split order point.',
        ),
    });
  }

  // --- delete ---
  remove(point: OrderPoint): void {
    this.error.set(null);
    this.pendingDelete.set(point);
  }
  cancelDelete(): void {
    this.pendingDelete.set(null);
  }
  confirmDelete(): void {
    const point = this.pendingDelete();
    if (!point) {
      return;
    }
    this.pendingDelete.set(null);
    this.orderPointService.delete(point.id).subscribe({
      next: () => this.orderPoints.update((list) => list.filter((p) => p.id !== point.id)),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  private sorted(list: OrderPoint[]): OrderPoint[] {
    return [...list].sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 400) {
      return 'Please check the fields.';
    }
    return `Failed to ${action} order point.`;
  }
}
