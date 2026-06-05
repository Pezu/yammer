import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { VatService, VatType } from './vat.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-vat-page',
  imports: [ReactiveFormsModule, ConfirmDialog],
  templateUrl: './vat-page.html',
  styleUrl: './vat-page.scss',
})
export class VatPage {
  private readonly vatService = inject(VatService);

  readonly vatTypes = signal<VatType[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly draft = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly pendingDelete = signal<VatType | null>(null);

  readonly draftValue = new FormControl<number | null>(null, [Validators.required, Validators.min(0)]);
  readonly editValue = new FormControl<number | null>(null, [Validators.required, Validators.min(0)]);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.vatService.list().subscribe({
      next: (vatTypes) => {
        this.vatTypes.set(vatTypes);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load VAT types.');
        this.loading.set(false);
      },
    });
  }

  startCreate(): void {
    this.editingId.set(null);
    this.draftValue.reset();
    this.error.set(null);
    this.draft.set(true);
  }

  cancelCreate(): void {
    this.draft.set(false);
  }

  saveCreate(): void {
    if (this.draftValue.invalid) {
      return;
    }
    this.vatService.create({ value: Number(this.draftValue.value) }).subscribe({
      next: (vat) => {
        this.vatTypes.update((list) => this.sorted([...list, vat]));
        this.draft.set(false);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
    });
  }

  startEdit(vat: VatType): void {
    this.draft.set(false);
    this.editingId.set(vat.id);
    this.editValue.setValue(vat.value);
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(id: string): void {
    if (this.editValue.invalid) {
      return;
    }
    this.vatService.update(id, { value: Number(this.editValue.value) }).subscribe({
      next: (updated) => {
        this.vatTypes.update((list) => this.sorted(list.map((v) => (v.id === id ? updated : v))));
        this.editingId.set(null);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
    });
  }

  remove(vat: VatType): void {
    this.error.set(null);
    this.pendingDelete.set(vat);
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  confirmDelete(): void {
    const vat = this.pendingDelete();
    if (!vat) {
      return;
    }
    this.pendingDelete.set(null);
    this.vatService.delete(vat.id).subscribe({
      next: () => this.vatTypes.update((list) => list.filter((v) => v.id !== vat.id)),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  private sorted(list: VatType[]): VatType[] {
    return [...list].sort((a, b) => a.value - b.value);
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 400) {
      return 'Please enter a non-negative value.';
    }
    return `Failed to ${action} VAT type.`;
  }
}
