import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AssignedOrderPoint, WaiterOrderPointService } from './waiter-order-point.service';

@Component({
  selector: 'app-waiter-tables-page',
  imports: [RouterLink],
  templateUrl: './waiter-tables-page.html',
  styleUrl: './waiter-tables-page.scss',
})
export class WaiterTablesPage {
  private readonly service = inject(WaiterOrderPointService);

  readonly items = signal<AssignedOrderPoint[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.service.mine().subscribe({
      next: (rows) => {
        this.items.set(
          [...rows].sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true })),
        );
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load your tables.');
        this.loading.set(false);
      },
    });
  }
}
