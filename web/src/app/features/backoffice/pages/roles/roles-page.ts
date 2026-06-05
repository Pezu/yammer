import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Role, RoleService } from './role.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-roles-page',
  imports: [ReactiveFormsModule, ConfirmDialog],
  templateUrl: './roles-page.html',
  styleUrl: './roles-page.scss',
})
export class RolesPage {
  private readonly roleService = inject(RoleService);

  readonly roles = signal<Role[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Whether the blank "new role" row is being added. */
  readonly draft = signal(false);
  /** id of the existing row being edited, or null. */
  readonly editingId = signal<string | null>(null);
  /** role pending delete confirmation, or null when the dialog is closed. */
  readonly pendingDelete = signal<Role | null>(null);

  readonly draftRole = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly editRole = new FormControl('', { nonNullable: true, validators: [Validators.required] });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.roleService.list().subscribe({
      next: (roles) => {
        this.roles.set(roles);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load roles.');
        this.loading.set(false);
      },
    });
  }

  // --- create (inline draft row) ---

  startCreate(): void {
    this.editingId.set(null);
    this.draftRole.reset();
    this.error.set(null);
    this.draft.set(true);
  }

  cancelCreate(): void {
    this.draft.set(false);
  }

  saveCreate(): void {
    if (this.draftRole.invalid) {
      return;
    }
    this.roleService.create(this.draftRole.value.trim()).subscribe({
      next: (role) => {
        this.roles.update((list) => this.sorted([...list, role]));
        this.draft.set(false);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
    });
  }

  // --- edit existing ---

  startEdit(role: Role): void {
    this.draft.set(false);
    this.editingId.set(role.id);
    this.editRole.setValue(role.role);
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(id: string): void {
    if (this.editRole.invalid) {
      return;
    }
    this.roleService.update(id, this.editRole.value.trim()).subscribe({
      next: (updated) => {
        this.roles.update((list) => this.sorted(list.map((r) => (r.id === id ? updated : r))));
        this.editingId.set(null);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
    });
  }

  // --- delete ---

  remove(role: Role): void {
    this.error.set(null);
    this.pendingDelete.set(role);
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  confirmDelete(): void {
    const role = this.pendingDelete();
    if (!role) {
      return;
    }
    this.pendingDelete.set(null);
    this.roleService.delete(role.id).subscribe({
      next: () => this.roles.update((list) => list.filter((r) => r.id !== role.id)),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  private sorted(list: Role[]): Role[] {
    return [...list].sort((a, b) => a.role.localeCompare(b.role));
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 409) {
      return 'A role with that name already exists.';
    }
    return `Failed to ${action} role.`;
  }
}
