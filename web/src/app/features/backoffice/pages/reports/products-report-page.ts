import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { OrderReportService, ProductReportRow } from './order.service';

@Component({
  selector: 'app-products-report-page',
  imports: [DecimalPipe],
  templateUrl: './products-report-page.html',
  styleUrl: './orders-report-page.scss',
})
export class ProductsReportPage {
  private readonly service = inject(OrderReportService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly products = signal<ProductReportRow[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly totalQuantity = computed(() => this.products().reduce((s, p) => s + p.quantity, 0));
  readonly maxQuantity = computed(() => Math.max(0, ...this.products().map((p) => p.quantity)));

  barPct(quantity: number): number {
    const max = this.maxQuantity();
    return max > 0 ? (quantity / max) * 100 : 0;
  }

  constructor() {
    this.service.products().subscribe({
      next: (products) => {
        this.products.set(products);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load products.');
        this.loading.set(false);
      },
    });
  }

  html(value: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(value);
  }
}
