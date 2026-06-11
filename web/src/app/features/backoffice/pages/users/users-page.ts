import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { User, UserService } from './user.service';
import { RoleService } from '../roles/role.service';
import { Client, ClientService } from '../clients/client.service';
import { AuthService, ROLE_SUPER } from '../../../../core/auth.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-users-page',
  imports: [ReactiveFormsModule, ConfirmDialog],
  templateUrl: './users-page.html',
  styleUrl: './users-page.scss',
})
export class UsersPage {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly roleService = inject(RoleService);
  private readonly clientService = inject(ClientService);
  private readonly auth = inject(AuthService);

  /** Whether the logged-in user is SUPER (chooses any client) vs scoped to one. */
  readonly isSuper = this.auth.isSuper;
  /** For a non-SUPER (ADMIN) operator, the single client they manage (from the JWT). */
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly users = signal<User[]>([]);
  readonly availableRoles = signal<string[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** SUPER-only header filter: '' = all clients. */
  readonly clientFilter = signal<string>('');
  /** Combo search query. */
  readonly comboSearch = signal('');
  /** Combo options — matches the search, capped at the first 5. */
  readonly comboOptions = computed(() => {
    const q = this.comboSearch().trim().toLowerCase();
    const matches = q
      ? this.clients().filter((c) => c.name.toLowerCase().includes(q))
      : this.clients();
    return matches.slice(0, 5);
  });
  /** Users shown after applying the client filter — SUPER users always show too. */
  readonly visibleUsers = computed(() => {
    const filter = this.clientFilter();
    if (!filter) {
      return this.users();
    }
    return this.users().filter((u) => u.clientId === filter || u.roles.includes(ROLE_SUPER));
  });

  /** SUPER must pick a client before the table is shown; others always see it. */
  readonly showTable = computed(() => !this.isSuper() || !!this.clientFilter());

  /** Custom client combo (styled dropdown, not a native select). */
  readonly comboOpen = signal(false);
  readonly selectedClientName = computed(
    () => this.clients().find((c) => c.id === this.clientFilter())?.name ?? 'Select a client…',
  );

  toggleCombo(): void {
    this.comboSearch.set('');
    this.comboOpen.update((open) => !open);
  }

  closeCombo(): void {
    this.comboOpen.set(false);
  }

  selectClient(id: string): void {
    this.clientFilter.set(id);
    this.comboOpen.set(false);
  }

  readonly draft = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly pendingDelete = signal<User | null>(null);

  /** Selected role names for the row being added / edited. */
  readonly draftRoles = signal<string[]>([]);
  readonly editRoles = signal<string[]>([]);

  /** Selected client id ('' = none) for the row being added / edited. */
  readonly draftClientId = signal<string>('');
  readonly editClientId = signal<string>('');

  // SUPER users have no client; the client picker is hidden/cleared for them.
  readonly draftIsSuper = computed(() => this.draftRoles().includes(ROLE_SUPER));
  readonly editIsSuper = computed(() => this.editRoles().includes(ROLE_SUPER));

  // password is required when creating, optional (blank = keep) when editing.
  readonly draftForm = this.newForm(true);
  readonly editForm = this.newForm(false);

  constructor() {
    this.load();
    this.roleService.list().subscribe({
      next: (roles) => this.availableRoles.set(roles.map((r) => r.role)),
    });
    this.clientService.list().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        // If there's only one client to choose from, select it by default.
        if (this.isSuper() && clients.length === 1 && !this.clientFilter()) {
          this.clientFilter.set(clients[0].id);
        }
      },
    });
  }


  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.userService.list().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load users.');
        this.loading.set(false);
      },
    });
  }

  // --- create ---

  startCreate(): void {
    this.editingId.set(null);
    this.draftForm.reset();
    this.draftRoles.set([]);
    // Client comes from context: SUPER uses the header filter, ADMIN their own client.
    this.draftClientId.set(this.isSuper() ? this.clientFilter() : this.ownClientId());
    this.error.set(null);
    this.draft.set(true);
  }

  cancelCreate(): void {
    this.draft.set(false);
  }

  saveCreate(): void {
    if (this.draftForm.invalid) {
      this.error.set('Please enter a username, password and a valid email.');
      return;
    }
    // Client id is contextual; the backend validates/forces it per the caller's role.
    const clientId = this.draftClientId() || null;
    this.userService
      .create({ ...this.draftForm.getRawValue(), roles: this.draftRoles(), clientId })
      .subscribe({
        next: (user) => {
          this.users.update((list) => this.sorted([...list, user]));
          this.draft.set(false);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
      });
  }

  // --- edit ---

  startEdit(user: User): void {
    this.draft.set(false);
    this.editingId.set(user.id);
    this.editForm.setValue({
      username: user.username,
      password: '',
      phone: user.phone ?? '',
      email: user.email ?? '',
    });
    this.editRoles.set([...user.roles]);
    // ADMIN operators are locked to their own client; SUPER keeps the user's client.
    this.editClientId.set(this.isSuper() ? user.clientId ?? '' : this.ownClientId());
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(id: string): void {
    if (this.editForm.invalid) {
      this.error.set('Please enter a username and a valid email.');
      return;
    }
    const clientId = this.editClientId() || null;
    this.userService
      .update(id, { ...this.editForm.getRawValue(), roles: this.editRoles(), clientId })
      .subscribe({
        next: (updated) => {
          this.users.update((list) => this.sorted(list.map((u) => (u.id === id ? updated : u))));
          this.editingId.set(null);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
      });
  }

  // --- delete ---

  remove(user: User): void {
    this.error.set(null);
    this.pendingDelete.set(user);
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  confirmDelete(): void {
    const user = this.pendingDelete();
    if (!user) {
      return;
    }
    this.pendingDelete.set(null);
    this.userService.delete(user.id).subscribe({
      next: () => this.users.update((list) => list.filter((u) => u.id !== user.id)),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  // --- role selection helpers ---

  toggleDraftRole(role: string): void {
    this.draftRoles.update((list) => this.toggle(list, role));
  }

  toggleEditRole(role: string): void {
    this.editRoles.update((list) => this.toggle(list, role));
  }

  private toggle(list: string[], role: string): string[] {
    return list.includes(role) ? list.filter((r) => r !== role) : [...list, role];
  }

  private newForm(passwordRequired: boolean) {
    return this.fb.nonNullable.group({
      username: ['', [Validators.required]],
      password: ['', passwordRequired ? [Validators.required] : []],
      phone: [''],
      email: ['', [Validators.email]],
    });
  }

  private sorted(list: User[]): User[] {
    return [...list].sort((a, b) => a.username.localeCompare(b.username));
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 409) {
      return 'A user with that username already exists.';
    }
    if (err.status === 400) {
      return 'Please check the fields.';
    }
    return `Failed to ${action} user.`;
  }
}
