import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Customer,
  CustomerImportResult,
  CustomerInput,
  CustomerService,
} from './customer.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-customers-page',
  imports: [ReactiveFormsModule, ConfirmDialog],
  templateUrl: './customers-page.html',
  styleUrl: './customers-page.scss',
})
export class CustomersPage {
  private readonly customerService = inject(CustomerService);
  private readonly fb = inject(FormBuilder);

  // `customers` holds the CURRENT page only; `total` is the server-side row count.
  readonly customers = signal<Customer[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly draft = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly pendingDelete = signal<Customer | null>(null);

  readonly importing = signal(false);
  readonly importResult = signal<CustomerImportResult | null>(null);

  readonly page = signal(1);
  readonly pageSizes = [10, 50, 100];
  readonly pageSize = signal(50);
  readonly comboOpen = signal(false);

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.total() / this.pageSize())));
  readonly range = computed(() => {
    const total = this.total();
    if (total === 0) return '0';
    const start = (this.page() - 1) * this.pageSize() + 1;
    return `${start}–${Math.min(total, this.page() * this.pageSize())} of ${total}`;
  });

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(255)]],
    lastName: ['', [Validators.required, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(50)]],
    email: ['', [Validators.email, Validators.maxLength(255)]],
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.customerService.listPaged(this.page() - 1, this.pageSize()).subscribe({
      next: (res) => {
        // If the page went empty after a deletion (and we're not on page 1), step back.
        if (res.content.length === 0 && res.total > 0 && this.page() > 1) {
          this.page.set(Math.max(1, Math.ceil(res.total / this.pageSize())));
          this.load();
          return;
        }
        this.customers.set(res.content);
        this.total.set(res.total);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load customers.');
        this.loading.set(false);
      },
    });
  }

  prev(): void {
    if (this.page() <= 1) return;
    this.page.update((p) => Math.max(1, p - 1));
    this.load();
  }

  next(): void {
    if (this.page() >= this.totalPages()) return;
    this.page.update((p) => Math.min(this.totalPages(), p + 1));
    this.load();
  }

  toggleCombo(): void {
    this.comboOpen.update((o) => !o);
  }

  closeCombo(): void {
    this.comboOpen.set(false);
  }

  setPageSize(n: number): void {
    this.pageSize.set(n);
    this.page.set(1);
    this.comboOpen.set(false);
    this.load();
  }

  startCreate(): void {
    this.editingId.set(null);
    this.form.reset();
    this.error.set(null);
    this.draft.set(true);
  }

  cancelCreate(): void {
    this.draft.set(false);
  }

  saveCreate(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.customerService.create(this.payload()).subscribe({
      next: () => {
        this.draft.set(false);
        this.load();
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
    });
  }

  startEdit(customer: Customer): void {
    this.draft.set(false);
    this.editingId.set(customer.id);
    this.error.set(null);
    this.form.reset({
      firstName: customer.firstName,
      lastName: customer.lastName,
      phone: customer.phone ?? '',
      email: customer.email ?? '',
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(id: string): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.customerService.update(id, this.payload()).subscribe({
      next: () => {
        this.editingId.set(null);
        this.load();
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
    });
  }

  remove(customer: Customer): void {
    this.error.set(null);
    this.pendingDelete.set(customer);
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  confirmDelete(): void {
    const customer = this.pendingDelete();
    if (!customer) {
      return;
    }
    this.pendingDelete.set(null);
    this.customerService.delete(customer.id).subscribe({
      next: () => this.load(),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  importFile(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file
    if (!file) {
      return;
    }
    this.error.set(null);
    this.importResult.set(null);
    this.importing.set(true);
    this.customerService.importXlsx(file).subscribe({
      next: (result) => {
        this.importing.set(false);
        this.importResult.set(result);
        this.page.set(1);
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.importing.set(false);
        this.error.set(this.importMessage(err));
      },
    });
  }

  dismissImportResult(): void {
    this.importResult.set(null);
  }

  fullName(c: Customer): string {
    return `${c.firstName} ${c.lastName}`.trim();
  }

  private payload(): CustomerInput {
    const v = this.form.getRawValue();
    return {
      firstName: v.firstName.trim(),
      lastName: v.lastName.trim(),
      phone: v.phone.trim() || null,
      email: v.email.trim() || null,
    };
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 400) {
      return 'Please check the form — first and last name are required, and the email must be valid.';
    }
    return `Failed to ${action} customer.`;
  }

  private importMessage(err: HttpErrorResponse): string {
    const detail = err.error?.message;
    if (err.status === 400 && typeof detail === 'string' && detail) {
      return detail;
    }
    if (err.status === 413) {
      return 'That file is too large to import.';
    }
    return 'Failed to import customers. Make sure the file is a valid .xlsx.';
  }
}
