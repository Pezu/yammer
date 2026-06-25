import { Component, ElementRef, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { AuthService } from '../../../../core/auth.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService } from '../locations/location.service';
import { Menu, MenuNode, MenuService } from './menu.service';
import { Event, EventService } from '../events/event.service';
import { VatService, VatType } from '../vat/vat.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';
import { TransparentImageDirective } from '../../../../shared/transparent-image.directive';

interface TreeNode {
  key: string;
  id: string | null;
  name: string;
  orderable: boolean;
  price: number | null;
  vatTypeId: string | null;
  imageObject: string | null;
  combined: boolean;
  children: TreeNode[];
  expanded: boolean;
}

@Component({
  selector: 'app-menu-page',
  imports: [NgTemplateOutlet, DecimalPipe, ConfirmDialog, TransparentImageDirective],
  templateUrl: './menu-page.html',
  styleUrl: './menu-page.scss',
})
export class MenuPage {
  private readonly auth = inject(AuthService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly menuService = inject(MenuService);
  private readonly eventService = inject(EventService);
  private readonly vatService = inject(VatService);

  readonly vatTypes = signal<VatType[]>([]);

  // image objects detected to have a transparent background — hidden, placeholder shown instead
  readonly transparentImages = signal<Set<string>>(new Set());
  markTransparent(object: string): void {
    this.transparentImages.update((s) => new Set(s).add(object));
  }

  /** Rich-text (HTML) name editor element inside the modal. */
  readonly nameEditor = viewChild<ElementRef<HTMLDivElement>>('nameEditor');

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly error = signal<string | null>(null);
  private keySeq = 0;

  // --- client combo (SUPER only) ---
  readonly clients = signal<Client[]>([]);
  readonly clientId = signal<string>('');
  readonly clientComboOpen = signal(false);
  readonly clientSearch = signal('');
  readonly clientOptions = computed(() => {
    const q = this.clientSearch().trim().toLowerCase();
    return (q ? this.clients().filter((c) => c.name.toLowerCase().includes(q)) : this.clients()).slice(0, 5);
  });
  readonly clientName = computed(
    () => this.clients().find((c) => c.id === this.clientId())?.name ?? 'Select a client…',
  );
  readonly clientChosen = computed(() => (this.isSuper() ? !!this.clientId() : true));

  // --- location combo ---
  readonly locations = signal<Location[]>([]);
  readonly locationId = signal<string>('');
  readonly locationComboOpen = signal(false);
  readonly locationSearch = signal('');
  readonly locationOptions = computed(() => {
    const q = this.locationSearch().trim().toLowerCase();
    return (q ? this.locations().filter((l) => l.name.toLowerCase().includes(q)) : this.locations()).slice(0, 5);
  });
  readonly locationName = computed(
    () => this.locations().find((l) => l.id === this.locationId())?.name ?? 'Select a location…',
  );

  // --- event combo ---
  readonly events = signal<Event[]>([]);
  readonly eventId = signal<string>('');
  readonly eventComboOpen = signal(false);
  readonly eventSearch = signal('');
  readonly eventOptions = computed(() => {
    const q = this.eventSearch().trim().toLowerCase();
    return (q ? this.events().filter((e) => e.name.toLowerCase().includes(q)) : this.events()).slice(0, 5);
  });
  readonly eventName = computed(
    () => this.events().find((e) => e.id === this.eventId())?.name ?? 'Select an event…',
  );

  // --- menu selector ---
  readonly menus = signal<Menu[]>([]);
  readonly menuId = signal<string>('');
  readonly menuComboOpen = signal(false);
  readonly addingMenu = signal(false);
  readonly pendingDeleteMenu = signal<Menu | null>(null);
  readonly selectedMenuName = computed(
    () => this.menus().find((m) => m.id === this.menuId())?.name ?? 'Select a menu…',
  );
  readonly selectedMenu = computed(() => this.menus().find((m) => m.id === this.menuId()) ?? null);

  // --- tree ---
  readonly tree = signal<TreeNode[]>([]);
  readonly saving = signal(false);
  private saveTimer: ReturnType<typeof setTimeout> | null = null;
  private saveSeq = 0;

  // --- item editor modal ---
  readonly showModal = signal(false);
  readonly editingNode = signal<TreeNode | null>(null); // null = creating
  readonly modalOrderable = signal(false); // editing/creating a product?
  readonly formName = signal('');
  readonly formPrice = signal<number | null>(null);
  readonly formVatTypeId = signal<string>('');
  readonly vatComboOpen = signal(false);
  readonly selectedVatName = computed(() => {
    const id = this.formVatTypeId();
    if (!id) {
      return 'None';
    }
    const vat = this.vatTypes().find((v) => v.id === id);
    return vat ? `${vat.value}%` : 'Select VAT…';
  });
  /** key of the product row whose inline VAT combo is open. */
  readonly vatRowKey = signal<string | null>(null);
  // --- item image (category/product) ---
  readonly formImageObject = signal<string | null>(null);
  readonly uploadingImage = signal(false);
  private modalParent: TreeNode | null = null; // parent for a new node (null = root)

  constructor() {
    if (this.isSuper()) {
      this.clientService.list().subscribe({
        next: (clients) => {
          this.clients.set(clients);
          if (clients.length === 1) {
            this.selectClient(clients[0].id);
          }
        },
      });
    } else {
      this.loadLocations(this.ownClientId());
    }
    this.loadVatTypes();

    // Seed the contenteditable name editor with the current HTML when the modal opens.
    effect(() => {
      const el = this.nameEditor()?.nativeElement;
      if (this.showModal() && el) {
        untracked(() => {
          el.innerHTML = this.formName();
          el.focus();
        });
      }
    });
  }

  // --- rich-text (HTML) name editor ---
  syncName(): void {
    const el = this.nameEditor()?.nativeElement;
    if (el) {
      this.formName.set(el.innerHTML);
    }
  }
  exec(command: string): void {
    document.execCommand(command);
    this.nameEditor()?.nativeElement.focus();
    this.syncName();
  }
  execFontSize(size: string): void {
    if (size) {
      document.execCommand('fontSize', false, size);
    }
    this.nameEditor()?.nativeElement.focus();
    this.syncName();
  }
  execColor(color: string): void {
    document.execCommand('foreColor', false, color);
    this.nameEditor()?.nativeElement.focus();
    this.syncName();
  }
  onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const text = event.clipboardData?.getData('text/plain') ?? '';
    document.execCommand('insertText', false, text);
    this.syncName();
  }
  private loadVatTypes(): void {
    this.vatService.list().subscribe({
      next: (v) => {
        this.vatTypes.set(v);
        // when adding a product, default to 21% once the list is available
        if (this.showModal() && !this.editingNode() && this.modalOrderable() && !this.formVatTypeId()) {
          this.formVatTypeId.set(this.defaultVatTypeId());
        }
      },
      error: () => this.error.set('Failed to load VAT types.'),
    });
  }

  /** The id of the 21% VAT type (the default for new products), or '' if none. */
  private defaultVatTypeId(): string {
    return this.vatTypes().find((v) => Number(v.value) === 21)?.id ?? '';
  }

  private plainText(html: string): string {
    const tmp = document.createElement('div');
    tmp.innerHTML = html;
    return tmp.textContent ?? '';
  }
  /** True when the name editor has visible text (ignoring markup). */
  hasName(): boolean {
    return this.plainText(this.formName()).trim().length > 0;
  }

  /** Short VAT label for a product row, e.g. "19%". */
  vatLabel(id: string | null): string | null {
    if (!id) {
      return null;
    }
    const vat = this.vatTypes().find((v) => v.id === id);
    return vat ? `${vat.value}%` : null;
  }

  // --- client ---
  toggleClientCombo(): void {
    this.clientSearch.set('');
    this.clientComboOpen.update((o) => !o);
  }
  closeClientCombo(): void {
    this.clientComboOpen.set(false);
  }
  selectClient(id: string): void {
    this.clientId.set(id);
    this.clientComboOpen.set(false);
    this.resetLocation();
    this.loadLocations(id);
  }
  private loadLocations(clientId: string): void {
    if (!clientId) {
      this.locations.set([]);
      return;
    }
    this.locationService.list(clientId).subscribe({
      next: (locations) => {
        this.locations.set(locations);
        if (locations.length === 1) {
          this.selectLocation(locations[0].id);
        }
      },
      error: () => this.error.set('Failed to load locations.'),
    });
  }
  private resetLocation(): void {
    this.locationId.set('');
    this.locations.set([]);
    this.resetEvent();
  }

  // --- location ---
  toggleLocationCombo(): void {
    this.locationSearch.set('');
    this.locationComboOpen.update((o) => !o);
  }
  closeLocationCombo(): void {
    this.locationComboOpen.set(false);
  }
  selectLocation(id: string): void {
    this.locationId.set(id);
    this.locationComboOpen.set(false);
    this.resetEvent();
    this.loadEvents(id);
  }
  private loadEvents(locationId: string): void {
    this.eventService.list(locationId).subscribe({
      next: (events) => {
        this.events.set(events);
        if (events.length === 1) {
          this.selectEvent(events[0].id);
        }
      },
      error: () => this.error.set('Failed to load events.'),
    });
  }
  private resetEvent(): void {
    this.eventId.set('');
    this.events.set([]);
    this.resetMenus();
  }

  // --- event ---
  toggleEventCombo(): void {
    this.eventSearch.set('');
    this.eventComboOpen.update((o) => !o);
  }
  closeEventCombo(): void {
    this.eventComboOpen.set(false);
  }
  selectEvent(id: string): void {
    this.eventId.set(id);
    this.eventComboOpen.set(false);
    this.resetMenus();
    this.loadMenus();
  }
  private loadMenus(): void {
    if (!this.locationId() || !this.eventId()) {
      return;
    }
    this.menuService.listMenus(this.locationId(), this.eventId()).subscribe({
      next: (menus) => {
        this.menus.set(menus);
        if (menus.length) {
          this.selectMenu(menus[0].id);
        }
      },
      error: () => this.error.set('Failed to load menus.'),
    });
  }
  private resetMenus(): void {
    this.cancelPendingSave();
    this.menus.set([]);
    this.menuId.set('');
    this.tree.set([]);
    this.addingMenu.set(false);
  }

  // --- menus ---
  toggleMenuCombo(): void {
    this.addingMenu.set(false);
    this.menuComboOpen.update((o) => !o);
  }
  closeMenuCombo(): void {
    this.menuComboOpen.set(false);
    this.addingMenu.set(false);
  }
  selectMenu(id: string): void {
    this.cancelPendingSave();
    this.menuId.set(id);
    this.menuComboOpen.set(false);
    this.loadTree(id);
  }
  private loadTree(menuId: string): void {
    this.menuService.getTree(menuId).subscribe({
      next: (nodes) => this.tree.set(this.toTree(nodes)),
      error: () => this.error.set('Failed to load menu.'),
    });
  }
  startAddMenu(): void {
    this.addingMenu.set(true);
  }
  cancelAddMenu(): void {
    this.addingMenu.set(false);
  }
  createMenu(name: string): void {
    const trimmed = name.trim();
    if (!trimmed) {
      return;
    }
    this.menuService.createMenu(this.locationId(), this.eventId(), trimmed).subscribe({
      next: (menu) => {
        this.menus.update((list) => [...list, menu].sort((a, b) => a.name.localeCompare(b.name)));
        this.addingMenu.set(false);
        this.selectMenu(menu.id);
      },
      error: () => this.error.set('Failed to create menu.'),
    });
  }
  removeMenu(menu: Menu): void {
    this.pendingDeleteMenu.set(menu);
  }
  cancelDeleteMenu(): void {
    this.pendingDeleteMenu.set(null);
  }
  confirmDeleteMenu(): void {
    const menu = this.pendingDeleteMenu();
    if (!menu) {
      return;
    }
    this.pendingDeleteMenu.set(null);
    this.menuService.deleteMenu(menu.id).subscribe({
      next: () => {
        this.menus.update((list) => list.filter((m) => m.id !== menu.id));
        if (this.menuId() === menu.id) {
          this.menuId.set('');
          this.tree.set([]);
          const rest = this.menus();
          if (rest.length) {
            this.selectMenu(rest[0].id);
          }
        }
      },
      error: () => this.error.set('Failed to delete menu.'),
    });
  }

  // --- item editor modal ---
  openAddCategory(): void {
    this.openCreate(null, false);
  }
  openAddChild(parent: TreeNode, orderable: boolean): void {
    this.openCreate(parent, orderable);
  }
  private openCreate(parent: TreeNode | null, orderable: boolean): void {
    if (orderable) {
      this.loadVatTypes(); // refresh so the combo reflects the current VAT page
    }
    this.modalParent = parent;
    this.editingNode.set(null);
    this.modalOrderable.set(orderable);
    this.formName.set('');
    this.formPrice.set(orderable ? 0 : null);
    this.formVatTypeId.set(orderable ? this.defaultVatTypeId() : '');
    this.formImageObject.set(null);
    this.vatComboOpen.set(false);
    this.showModal.set(true);
  }
  openEdit(node: TreeNode): void {
    if (node.orderable) {
      this.loadVatTypes(); // refresh so the combo reflects the current VAT page
    }
    this.modalParent = null;
    this.editingNode.set(node);
    this.modalOrderable.set(node.orderable);
    this.formName.set(node.name);
    this.formPrice.set(node.price);
    this.formVatTypeId.set(node.vatTypeId ?? '');
    this.formImageObject.set(node.imageObject ?? null);
    this.vatComboOpen.set(false);
    this.showModal.set(true);
  }
  closeModal(): void {
    this.showModal.set(false);
    this.vatComboOpen.set(false);
  }

  // --- item image ---
  onImageSelected(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file
    if (!file) {
      return;
    }
    this.uploadingImage.set(true);
    this.menuService.uploadImage(file).subscribe({
      next: ({ object }) => {
        this.formImageObject.set(object);
        this.uploadingImage.set(false);
      },
      error: () => this.uploadingImage.set(false),
    });
  }
  removeImage(): void {
    this.formImageObject.set(null);
  }
  imageUrl(object: string): string {
    return this.menuService.imageUrl(object);
  }

  /** Inline image upload from a tree row (no modal): upload, assign to the node, auto-save. */
  uploadRowImage(node: TreeNode, input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file
    if (!file) {
      return;
    }
    this.menuService.uploadImage(file).subscribe({
      next: ({ object }) => {
        node.imageObject = object;
        this.bump();
        this.scheduleSave();
      },
      error: () => {},
    });
  }

  // VAT custom combo (no search)
  toggleVatCombo(): void {
    const opening = !this.vatComboOpen();
    if (opening) {
      this.loadVatTypes(); // always show the current VAT page values
    }
    this.vatComboOpen.set(opening);
  }
  closeVatCombo(): void {
    this.vatComboOpen.set(false);
  }
  selectVat(id: string): void {
    this.formVatTypeId.set(id);
    this.vatComboOpen.set(false);
  }

  // inline VAT combo on a product row
  toggleRowVat(node: TreeNode): void {
    if (this.vatRowKey() === node.key) {
      this.vatRowKey.set(null);
    } else {
      this.loadVatTypes();
      this.vatRowKey.set(node.key);
    }
  }
  closeRowVat(): void {
    this.vatRowKey.set(null);
  }
  selectRowVat(node: TreeNode, id: string): void {
    node.vatTypeId = id;
    this.vatRowKey.set(null);
    this.bump();
    this.scheduleSave();
  }
  toggleCombined(node: TreeNode): void {
    node.combined = !node.combined;
    this.bump();
    this.scheduleSave();
  }
  saveItem(): void {
    if (!this.hasName()) {
      return;
    }
    const name = this.formName();
    const orderable = this.modalOrderable();
    const price = orderable ? this.formPrice() : null;
    const vatTypeId = orderable ? this.formVatTypeId() || null : null;
    const imageObject = this.formImageObject();
    const editing = this.editingNode();
    if (editing) {
      editing.name = name;
      editing.price = price;
      editing.vatTypeId = vatTypeId;
      editing.imageObject = imageObject;
    } else {
      const node = this.newNode(orderable);
      node.name = name;
      node.price = price;
      node.vatTypeId = vatTypeId;
      node.imageObject = imageObject;
      if (this.modalParent) {
        this.modalParent.children.push(node);
        this.modalParent.children.sort(byCategoryFirst);
        this.modalParent.expanded = true;
      } else {
        this.tree().push(node);
        this.tree().sort(byCategoryFirst);
      }
    }
    this.showModal.set(false);
    this.bump();
    this.scheduleSave();
  }

  // --- tree structure (auto-saves) ---
  removeNode(siblings: TreeNode[], node: TreeNode): void {
    const i = siblings.indexOf(node);
    if (i >= 0) {
      siblings.splice(i, 1);
    }
    this.bump();
    this.scheduleSave();
  }
  // expand/collapse are view-only — not persisted.
  toggleExpand(node: TreeNode): void {
    node.expanded = !node.expanded;
    this.bump();
  }
  expandAll(): void {
    this.walk(this.tree(), (n) => (n.orderable ? null : (n.expanded = true)));
    this.bump();
  }
  collapseAll(): void {
    this.walk(this.tree(), (n) => (n.orderable ? null : (n.expanded = false)));
    this.bump();
  }

  /** Debounced auto-save. The server response is not merged back so editing keeps focus. */
  private scheduleSave(): void {
    if (this.saveTimer) {
      clearTimeout(this.saveTimer);
    }
    this.saveTimer = setTimeout(() => this.doSave(), 400);
  }
  private cancelPendingSave(): void {
    if (this.saveTimer) {
      clearTimeout(this.saveTimer);
      this.saveTimer = null;
    }
  }
  private doSave(): void {
    const menuId = this.menuId();
    if (!menuId) {
      return;
    }
    this.saving.set(true);
    const seq = ++this.saveSeq;
    const sentTree = this.tree();
    this.menuService.saveTree(menuId, this.toMenuNodes(sentTree)).subscribe({
      next: (saved) => {
        // Adopt server-assigned ids for newly-created nodes so the next save updates
        // them in place instead of re-inserting (which would churn their ids). Ignore
        // stale responses; only fill null ids so editing isn't disturbed.
        if (seq === this.saveSeq) {
          this.adoptIds(sentTree, saved);
        }
        this.saving.set(false);
        this.error.set(null);
      },
      error: () => {
        this.saving.set(false);
        this.error.set('Failed to save menu.');
      },
    });
  }

  /** Copy server ids onto matching tree nodes (same order), filling only missing ids. */
  private adoptIds(treeNodes: TreeNode[], savedNodes: MenuNode[]): void {
    if (treeNodes.length !== savedNodes.length) {
      return; // structure changed since send — a later save will reconcile
    }
    for (let i = 0; i < treeNodes.length; i++) {
      const t = treeNodes[i];
      const s = savedNodes[i];
      if (t.id == null && s.id) {
        t.id = s.id;
      }
      this.adoptIds(t.children, s.children ?? []);
    }
  }

  private newNode(orderable: boolean): TreeNode {
    return {
      key: 'n' + ++this.keySeq,
      id: null,
      name: '',
      orderable,
      price: orderable ? 0 : null,
      vatTypeId: null,
      imageObject: null,
      combined: false,
      children: [],
      expanded: true,
    };
  }
  private walk(nodes: TreeNode[], fn: (n: TreeNode) => unknown): void {
    for (const n of nodes) {
      fn(n);
      this.walk(n.children, fn);
    }
  }
  private bump(): void {
    this.tree.set([...this.tree()]);
  }
  private toTree(nodes: MenuNode[]): TreeNode[] {
    return nodes
      .map((n) => ({
        key: 'n' + ++this.keySeq,
        id: n.id ?? null,
        name: n.name,
        orderable: n.orderable,
        price: n.price,
        vatTypeId: n.vatTypeId ?? null,
        imageObject: n.imageObject ?? null,
        combined: n.combined ?? false,
        children: this.toTree(n.children ?? []),
        expanded: true,
      }))
      .sort(byCategoryFirst);
  }
  private toMenuNodes(nodes: TreeNode[]): MenuNode[] {
    return nodes.map((n) => ({
      id: n.id,
      name: n.name,
      orderable: n.orderable,
      price: n.orderable ? n.price : null,
      vatTypeId: n.orderable ? n.vatTypeId : null,
      imageObject: n.imageObject,
      combined: n.orderable ? n.combined : false,
      children: this.toMenuNodes(n.children),
    }));
  }
}

/** Sort comparator: categories (orderable=false) before products among siblings. */
function byCategoryFirst(a: { orderable: boolean }, b: { orderable: boolean }): number {
  return Number(a.orderable) - Number(b.orderable);
}
