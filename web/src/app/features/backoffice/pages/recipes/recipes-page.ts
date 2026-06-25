import { Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../../../core/auth.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService } from '../locations/location.service';
import { Event, EventService } from '../events/event.service';
import { RecipeItem, RecipeService } from './recipe.service';

/**
 * Recipes (Rețetar): pick client (SUPER only) → location → event, then list every product flagged
 * "Combined" across all of that location/event's menus.
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

  readonly clients = signal<Client[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly events = signal<Event[]>([]);
  readonly items = signal<RecipeItem[]>([]);

  readonly clientId = signal<string>('');
  readonly locationId = signal<string>('');
  readonly eventId = signal<string>('');

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly clientChosen = computed(() => (this.isSuper() ? !!this.clientId() : true));

  constructor() {
    if (this.isSuper()) {
      this.clientService.list().subscribe({
        next: (cs) => this.clients.set(cs),
        error: () => this.error.set('Failed to load clients.'),
      });
    } else {
      this.loadLocations(this.auth.clientId() ?? '');
    }
  }

  selectClient(id: string): void {
    this.clientId.set(id);
    this.resetFrom('location');
    this.loadLocations(id);
  }

  selectLocation(id: string): void {
    this.locationId.set(id);
    this.resetFrom('event');
    if (!id) return;
    this.loadEvents(id);
    this.loadRecipes();
  }

  selectEvent(id: string): void {
    this.eventId.set(id);
    this.loadRecipes();
  }

  private loadLocations(clientId: string): void {
    if (!clientId) return;
    this.locationService.list(clientId).subscribe({
      next: (ls) => this.locations.set(ls),
      error: () => this.error.set('Failed to load locations.'),
    });
  }

  private loadEvents(locationId: string): void {
    this.eventService.list(locationId).subscribe({
      next: (es) => {
        this.events.set(es);
        if (es.length === 1) {
          this.selectEvent(es[0].id);
        }
      },
      error: () => this.error.set('Failed to load events.'),
    });
  }

  private loadRecipes(): void {
    if (!this.locationId()) return;
    this.loading.set(true);
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

  /** Clear everything downstream of the given level. */
  private resetFrom(level: 'location' | 'event'): void {
    if (level === 'location') {
      this.locationId.set('');
      this.locations.set([]);
    }
    this.eventId.set('');
    this.events.set([]);
    this.items.set([]);
  }
}
