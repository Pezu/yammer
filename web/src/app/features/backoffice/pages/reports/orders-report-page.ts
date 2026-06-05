import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Order, OrderReportService } from './order.service';

@Component({
  selector: 'app-orders-report-page',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './orders-report-page.html',
  styleUrl: './orders-report-page.scss',
})
export class OrdersReportPage {
  private readonly service = inject(OrderReportService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly orders = signal<Order[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.service.list().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load orders.');
        this.loading.set(false);
      },
    });
  }

  html(value: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(value);
  }
}
