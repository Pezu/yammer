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
  readonly splitting = signal<string | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
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

  /** Split a pay-later table into a new sibling (copies its configuration). */
  split(op: AssignedOrderPoint, event: Event): void {
    // the card is a link — don't navigate when the split button is tapped
    event.preventDefault();
    event.stopPropagation();
    if (this.splitting()) {
      return;
    }
    this.splitting.set(op.id);
    this.service.split(op.id).subscribe({
      next: () => {
        this.splitting.set(null);
        this.load(); // the new sibling shows up (same parent → already assigned)
      },
      error: () => {
        this.splitting.set(null);
        this.error.set('Could not split the table.');
      },
    });
  }
}
