import { Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../../../core/auth.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService } from '../locations/location.service';
import { Event, EventService } from '../events/event.service';
import { RecipeComponent, RecipeItem, RecipeService } from './recipe.service';

/**
 * Recipes (Rețetar): pick client (SUPER only) → location → event using the same combo selectors as
 * the Menu page, then list every product flagged "Combined" across that location/event's menus.
 */
@Component({
  selector: 'app-recipes-page',
  templateUrl: './recipes-page.html',
  styleUrl: './recipes-page.scss',
})
export class RecipesPage {
  private readonly auth = inject(AuthService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly eventService = inject(EventService);
  private readonly recipeService = inject(RecipeService);

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly error = signal<string | null>(null);
  readonly loading = signal(false);
  readonly items = signal<RecipeItem[]>([]);

  // --- selected combined product + its recipe components ---
  readonly selectedItem = signal<RecipeItem | null>(null);
  readonly components = signal<RecipeComponent[]>([]);

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

  // --- client ---
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

  // --- location ---
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
    this.loadRecipes();
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
  private resetEvent(): void {
    this.eventId.set('');
    this.events.set([]);
    this.items.set([]);
  }

  // --- event ---
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
    this.loadRecipes();
  }

  private loadRecipes(): void {
    if (!this.locationId()) return;
    this.loading.set(true);
    this.selectedItem.set(null);
    this.components.set([]);
    this.recipeService.list(this.locationId(), this.eventId()).subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
        this.error.set(null);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Failed to load recipes.');
      },
    });
  }

  // --- recipe components (second table) ---
  selectItem(item: RecipeItem): void {
    this.selectedItem.set(item);
    this.components.set([]);
    this.recipeService.listComponents(item.id).subscribe({
      next: (cs) => this.components.set(cs),
      error: () => this.error.set('Failed to load recipe.'),
    });
  }

  addComponent(): void {
    const item = this.selectedItem();
    if (!item) return;
    this.recipeService
      .createComponent(item.id, { name: '', quantity: null, unit: null, percentage: null })
      .subscribe({
        next: (c) => this.components.update((cs) => [...cs, c]),
        error: () => this.error.set('Failed to add component.'),
      });
  }

  /** Persist a row after an inline edit. */
  saveComponent(c: RecipeComponent): void {
    this.recipeService
      .updateComponent(c.id, {
        name: c.name,
        quantity: c.quantity,
        unit: c.unit,
        percentage: c.percentage,
      })
      .subscribe({ error: () => this.error.set('Failed to save component.') });
  }

  deleteComponent(c: RecipeComponent): void {
    this.recipeService.deleteComponent(c.id).subscribe({
      next: () => this.components.update((cs) => cs.filter((x) => x.id !== c.id)),
      error: () => this.error.set('Failed to delete component.'),
    });
  }

  // inline-edit field setters (mutate the row, then persist)
  setName(c: RecipeComponent, v: string): void {
    c.name = v;
    this.saveComponent(c);
  }
  setQuantity(c: RecipeComponent, v: string): void {
    c.quantity = v === '' ? null : Number(v);
    this.saveComponent(c);
  }
  setUnit(c: RecipeComponent, v: string): void {
    c.unit = v || null;
    this.saveComponent(c);
  }
  setPercentage(c: RecipeComponent, v: string): void {
    c.percentage = v === '' ? null : Number(v);
    this.saveComponent(c);
  }
}
