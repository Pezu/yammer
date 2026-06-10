import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Client, ClientService } from './client.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-clients-page',
  imports: [ReactiveFormsModule, ConfirmDialog],
  templateUrl: './clients-page.html',
  styleUrl: './clients-page.scss',
})
export class ClientsPage {
  private readonly fb = inject(FormBuilder);
  private readonly clientService = inject(ClientService);

  readonly clients = signal<Client[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Whether the blank "new client" row is being added. */
  readonly draft = signal(false);
  /** id of the existing row being edited, or null. */
  readonly editingId = signal<string | null>(null);
  /** client pending delete confirmation, or null when the dialog is closed. */
  readonly pendingDelete = signal<Client | null>(null);

  readonly draftForm = this.newForm();
  readonly editForm = this.newForm();

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.clientService.list().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load clients.');
        this.loading.set(false);
      },
    });
  }

  // --- create (inline draft row) ---

  startCreate(): void {
    this.editingId.set(null);
    this.draftForm.reset();
    this.error.set(null);
    this.draft.set(true);
  }

  cancelCreate(): void {
    this.draft.set(false);
  }

  saveCreate(): void {
    if (this.draftForm.invalid) {
      this.error.set('Please enter a name and a valid email.');
      return;
    }
    this.clientService.create(this.draftForm.getRawValue()).subscribe({
      next: (client) => {
        this.clients.update((list) => this.sorted([...list, client]));
        this.draft.set(false);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
    });
  }

  // --- edit existing ---

  startEdit(client: Client): void {
    this.draft.set(false);
    this.editingId.set(client.id);
    this.editForm.setValue({
      name: client.name,
      phone: client.phone ?? '',
      email: client.email ?? '',
    });
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(id: string): void {
    if (this.editForm.invalid) {
      this.error.set('Please enter a name and a valid email.');
      return;
    }
    this.clientService.update(id, this.editForm.getRawValue()).subscribe({
      next: (updated) => {
        this.clients.update((list) => this.sorted(list.map((c) => (c.id === id ? updated : c))));
        this.editingId.set(null);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
    });
  }

  // --- logo ---

  /** Per-client cache-buster, bumped after each logo change. */
  private readonly logoVersion = signal<Record<string, number>>({});
  readonly uploadingLogo = signal<string | null>(null);

  logoUrl(client: Client): string {
    return this.clientService.logoUrl(client.id, this.logoVersion()[client.id] ?? 0);
  }

  onLogoSelected(client: Client, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file later
    if (!file) {
      return;
    }
    this.error.set(null);
    this.uploadingLogo.set(client.id);
    this.clientService.uploadLogo(client.id, file).subscribe({
      next: (updated) => {
        this.bumpLogo(updated.id);
        this.clients.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
        this.uploadingLogo.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.uploadingLogo.set(null);
        this.error.set(err.status === 415 ? 'Please choose an image file.' : 'Failed to upload logo.');
      },
    });
  }

  removeLogo(client: Client): void {
    this.error.set(null);
    this.clientService.deleteLogo(client.id).subscribe({
      next: (updated) => {
        this.bumpLogo(updated.id);
        this.clients.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
      },
      error: () => this.error.set('Failed to remove logo.'),
    });
  }

  private bumpLogo(id: string): void {
    this.logoVersion.update((m) => ({ ...m, [id]: (m[id] ?? 0) + 1 }));
  }

  // --- delete ---

  remove(client: Client): void {
    this.error.set(null);
    this.pendingDelete.set(client);
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  confirmDelete(): void {
    const client = this.pendingDelete();
    if (!client) {
      return;
    }
    this.pendingDelete.set(null);
    this.clientService.delete(client.id).subscribe({
      next: () => this.clients.update((list) => list.filter((c) => c.id !== client.id)),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  private newForm() {
    return this.fb.nonNullable.group({
      name: ['', [Validators.required]],
      phone: [''],
      email: ['', [Validators.email]],
    });
  }

  private sorted(list: Client[]): Client[] {
    return [...list].sort((a, b) => a.name.localeCompare(b.name));
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 400) {
      return 'Please enter a name and a valid email.';
    }
    return `Failed to ${action} client.`;
  }
}
