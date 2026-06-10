import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../core/auth.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService } from '../locations/location.service';
import { Menu, MenuService } from '../menu/menu.service';
import { Event, EventService } from '../events/event.service';
import { Integration, IntegrationService } from '../integrations/integration.service';
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
  private readonly eventService = inject(EventService);
  private readonly integrationService = inject(IntegrationService);
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

  toggleBatchServiceCombo(): void {
    this.batchServiceComboOpen.update((o) => !o);
  }
  closeBatchServiceCombo(): void {
    this.batchServiceComboOpen.set(false);
  }
  selectBatchService(id: string): void {
    this.batchServiceOrderPointId.set(id);
    this.batchServiceComboOpen.set(false);
  }

  toggleBatchPrinterCombo(): void {
    this.batchPrinterComboOpen.update((o) => !o);
  }
  closeBatchPrinterCombo(): void {
    this.batchPrinterComboOpen.set(false);
  }
  selectBatchPrinter(id: string): void {
    this.batchPrinterId.set(id);
    this.batchPrinterComboOpen.set(false);
  }

  toggleBatchCashRegisterCombo(): void {
    this.batchCashRegisterComboOpen.update((o) => !o);
  }
  closeBatchCashRegisterCombo(): void {
    this.batchCashRegisterComboOpen.set(false);
  }
  selectBatchCashRegister(id: string): void {
    this.batchCashRegisterId.set(id);
    this.batchCashRegisterComboOpen.set(false);
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

  // --- devices (printer + cash register) from the location's integrations ---
  readonly printers = signal<Integration[]>([]);
  readonly cashRegisters = signal<Integration[]>([]);
  deviceName(list: Integration[], id: string | null): string {
    return list.find((d) => d.id === id)?.name ?? '';
  }
  printerLabel(id: string): string {
    return this.deviceName(this.printers(), id) || 'No printer';
  }
  cashRegisterLabel(id: string): string {
    return this.deviceName(this.cashRegisters(), id) || 'No cash register';
  }

  readonly draftPrinterComboOpen = signal(false);
  readonly editPrinterComboOpen = signal(false);
  readonly draftCashRegisterComboOpen = signal(false);
  readonly editCashRegisterComboOpen = signal(false);

  toggleDraftPrinterCombo(): void {
    this.draftPrinterComboOpen.update((o) => !o);
  }
  closeDraftPrinterCombo(): void {
    this.draftPrinterComboOpen.set(false);
  }
  selectDraftPrinter(id: string): void {
    this.draftPrinterId.set(id);
    this.draftPrinterComboOpen.set(false);
  }
  toggleEditPrinterCombo(): void {
    this.editPrinterComboOpen.update((o) => !o);
  }
  closeEditPrinterCombo(): void {
    this.editPrinterComboOpen.set(false);
  }
  selectEditPrinter(id: string): void {
    this.editPrinterId.set(id);
    this.editPrinterComboOpen.set(false);
  }

  toggleDraftCashRegisterCombo(): void {
    this.draftCashRegisterComboOpen.update((o) => !o);
  }
  closeDraftCashRegisterCombo(): void {
    this.draftCashRegisterComboOpen.set(false);
  }
  selectDraftCashRegister(id: string): void {
    this.draftCashRegisterId.set(id);
    this.draftCashRegisterComboOpen.set(false);
  }
  toggleEditCashRegisterCombo(): void {
    this.editCashRegisterComboOpen.update((o) => !o);
  }
  closeEditCashRegisterCombo(): void {
    this.editCashRegisterComboOpen.set(false);
  }
  selectEditCashRegister(id: string): void {
    this.editCashRegisterId.set(id);
    this.editCashRegisterComboOpen.set(false);
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
  readonly draftPrinterId = signal<string>('');
  readonly draftCashRegisterId = signal<string>('');
  readonly editName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly editPayLater = signal(false);
  readonly editProtocol = signal(false);
  readonly editMenuId = signal<string>('');
  readonly editServiceOrderPointId = signal<string>('');
  readonly editPrinterId = signal<string>('');
  readonly editCashRegisterId = signal<string>('');

  // --- "add multiple" modal ---
  readonly batchModalOpen = signal(false);
  readonly batchCount = signal<number>(1);
  readonly batchPayLater = signal(false);
  readonly batchMenuId = signal<string>('');
  readonly batchServiceOrderPointId = signal<string>('');
  readonly batchServiceComboOpen = signal(false);
  readonly batchPrinterId = signal<string>('');
  readonly batchPrinterComboOpen = signal(false);
  readonly batchCashRegisterId = signal<string>('');
  readonly batchCashRegisterComboOpen = signal(false);
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
    this.printers.set([]);
    this.cashRegisters.set([]);
    this.resetEvent();
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
    this.loadDevices(id);
    this.loadEvents(id);
  }
  private loadDevices(locationId: string): void {
    this.integrationService.list(locationId, 'PRINTER').subscribe({
      next: (list) => this.printers.set(list),
      error: () => this.error.set('Failed to load printers.'),
    });
    this.integrationService.list(locationId, 'CASH_REGISTER').subscribe({
      next: (list) => this.cashRegisters.set(list),
      error: () => this.error.set('Failed to load cash registers.'),
    });
  }
  private resetEvent(): void {
    this.eventId.set('');
    this.events.set([]);
    this.resetTable();
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
    this.resetTable();
    this.loadMenus();
    this.loadOrderPoints();
  }
  private resetTable(): void {
    this.orderPoints.set([]);
    this.menus.set([]);
    this.draft.set(false);
    this.editingId.set(null);
    this.error.set(null);
  }
  private loadMenus(): void {
    if (!this.locationId() || !this.eventId()) {
      return;
    }
    this.menuService.listMenus(this.locationId(), this.eventId()).subscribe({
      next: (menus) => this.menus.set(menus),
      error: () => this.error.set('Failed to load menus.'),
    });
  }
  private loadOrderPoints(): void {
    if (!this.locationId() || !this.eventId()) {
      return;
    }
    this.loading.set(true);
    this.orderPointService.list(this.locationId(), this.eventId()).subscribe({
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
    this.draftPrinterId.set('');
    this.draftPrinterComboOpen.set(false);
    this.draftCashRegisterId.set('');
    this.draftCashRegisterComboOpen.set(false);
    this.error.set(null);
    this.draft.set(true);
  }
  cancelCreate(): void {
    this.draftMenuComboOpen.set(false);
    this.draftServiceComboOpen.set(false);
    this.draftPrinterComboOpen.set(false);
    this.draftCashRegisterComboOpen.set(false);
    this.draft.set(false);
  }
  saveCreate(): void {
    if (this.draftName.invalid || !this.locationId() || !this.eventId()) {
      return;
    }
    this.orderPointService
      .create({
        locationId: this.locationId(),
        eventId: this.eventId(),
        name: this.draftName.value.trim(),
        payLater: this.draftPayLater(),
        protocol: this.draftProtocol(),
        menuId: this.draftMenuId() || null,
        serviceOrderPointId: this.draftPayLater() ? this.draftServiceOrderPointId() || null : null,
        printerId: this.draftPrinterId() || null,
        cashRegisterId: this.draftCashRegisterId() || null,
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
    if (!this.locationId() || !this.eventId()) {
      return;
    }
    this.batchCount.set(1);
    this.batchPayLater.set(false);
    this.batchMenuId.set('');
    this.batchMenuComboOpen.set(false);
    this.batchServiceOrderPointId.set('');
    this.batchServiceComboOpen.set(false);
    this.batchPrinterId.set('');
    this.batchPrinterComboOpen.set(false);
    this.batchCashRegisterId.set('');
    this.batchCashRegisterComboOpen.set(false);
    this.error.set(null);
    this.batchModalOpen.set(true);
  }
  closeBatchModal(): void {
    this.batchMenuComboOpen.set(false);
    this.batchServiceComboOpen.set(false);
    this.batchPrinterComboOpen.set(false);
    this.batchCashRegisterComboOpen.set(false);
    this.batchModalOpen.set(false);
    this.savingBatch.set(false);
  }
  saveBatch(): void {
    const count = Number(this.batchCount());
    if (!this.locationId() || !this.eventId() || !Number.isInteger(count) || count < 1) {
      return;
    }
    this.savingBatch.set(true);
    this.orderPointService
      .createBatch({
        locationId: this.locationId(),
        eventId: this.eventId(),
        count,
        payLater: this.batchPayLater(),
        menuId: this.batchMenuId() || null,
        serviceOrderPointId: this.batchPayLater() ? this.batchServiceOrderPointId() || null : null,
        printerId: this.batchPrinterId() || null,
        cashRegisterId: this.batchCashRegisterId() || null,
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
    this.editPrinterId.set(point.printerId ?? '');
    this.editPrinterComboOpen.set(false);
    this.editCashRegisterId.set(point.cashRegisterId ?? '');
    this.editCashRegisterComboOpen.set(false);
    this.error.set(null);
  }
  cancelEdit(): void {
    this.editMenuComboOpen.set(false);
    this.editServiceComboOpen.set(false);
    this.editPrinterComboOpen.set(false);
    this.editCashRegisterComboOpen.set(false);
    this.editingId.set(null);
  }
  saveEdit(point: OrderPoint): void {
    if (this.editName.invalid) {
      return;
    }
    this.orderPointService
      .update(point.id, {
        locationId: point.locationId,
        eventId: point.eventId,
        name: this.editName.value.trim(),
        payLater: this.editPayLater(),
        protocol: this.editProtocol(),
        menuId: this.editMenuId() || null,
        serviceOrderPointId: this.editPayLater() ? this.editServiceOrderPointId() || null : null,
        printerId: this.editPrinterId() || null,
        cashRegisterId: this.editCashRegisterId() || null,
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
