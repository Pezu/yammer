import {
  Component,
  ElementRef,
  OnDestroy,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CustomerInfo,
  CustomerOrder,
  CustomerOrderPoint,
  CustomerOrderPointService,
  MenuNode,
} from './customer-order-point.service';
import { TransparentImageDirective } from '../../shared/transparent-image.directive';
import { timeAgo } from '../../shared/relative-time';
import { RouterLink } from '@angular/router';
import { LEGAL_LINKS } from './legal-page';

/**
 * Landing/ordering page a customer reaches by scanning an order point's QR code
 * (`/customer/order-point/:opId`). Shows the order point's default menu and lets the customer
 * build a cart and place a (pay-later) order. The event is resolved from the order point.
 */
@Component({
  selector: 'app-customer-order-point-page',
  imports: [DecimalPipe, NgTemplateOutlet, TransparentImageDirective, RouterLink],
  template: `
    <header class="topbar">
      <div class="brand">
        <img class="logo" [src]="logoSrc()" alt="logo" (error)="logoFailed.set(true)" />
      </div>
      <button type="button" class="hamburger" (click)="toggleMenu()" aria-label="Menu" aria-haspopup="true">
        <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
      </button>
    </header>

    @if (menuOpen()) {
      <div class="drawer-backdrop" (click)="closeMenu()"></div>
      <nav class="drawer">
        <button type="button" class="drawer-close" (click)="closeMenu()" aria-label="Close menu">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
        </button>
        <button type="button" class="drawer-item" [class.active]="activeView() === 'menu'" (click)="showMenu()">Menu</button>
        @if (hasOrdersTab()) {
          <button type="button" class="drawer-item" [class.active]="activeView() === 'orders'" (click)="showOrders()">Orders</button>
        }
        <div class="drawer-legal">
          @for (link of legalLinks; track link.slug) {
            <a class="drawer-legal-item" [routerLink]="['/legal', link.slug]" (click)="closeMenu()">{{ link.label }}</a>
          }
        </div>
      </nav>
    }

    <main class="cust">
      @if (loading()) {
        <p class="state">Loading…</p>
      } @else if (error()) {
        <p class="state err">{{ error() }}</p>
      } @else if (op(); as o) {
        @if (placed()) {
          <div class="placed" role="status">Your order has been sent. Thank you!</div>
        }

        @if (activeView() === 'orders') {
          @if (ordersLoading()) {
            <p class="state">Loading…</p>
          } @else if (custOrders().length === 0) {
            <p class="soon">No orders yet.</p>
          } @else {
            <div class="orders-list">
              @for (ord of custOrders(); track ord.id) {
                <div class="ord-card">
                  <div class="ord-head">
                    <span class="ord-no">Order #{{ ord.orderNo }}</span>
                    <span class="ord-status" [class]="'st-' + ord.status.toLowerCase()">{{ ord.status }}</span>
                  </div>
                  <div class="ord-when">{{ relativeTime(ord.createdAt) }}</div>
                  <div class="ord-items">
                    @for (it of ord.items; track $index) {
                      <div class="ord-line">
                        <span class="ord-line-name">{{ it.quantity }}× {{ it.name }}</span>
                        <span class="ord-line-amt">{{ (it.price || 0) * it.quantity | number: '1.2-2' }}</span>
                      </div>
                    }
                  </div>
                  <div class="ord-total"><span>Total</span><span>{{ ord.total | number: '1.2-2' }} RON</span></div>
                </div>
              }
            </div>
          }
        } @else if (o.menu.length > 0) {
          @if (topCategories().length > 1) {
            <nav class="cat-nav">
              @for (cat of topCategories(); track cat.id) {
                <button type="button" class="cat-chip" (click)="scrollTo(cat.id)" [innerHTML]="cat.name"></button>
              }
            </nav>
          }
          <div class="menu-tree">
            @for (node of o.menu; track node.id) {
              <ng-container *ngTemplateOutlet="nodeTpl; context: { $implicit: node, level: 0 }"></ng-container>
            }
          </div>
        } @else {
          <p class="soon">No menu available for this table yet.</p>
        }
      }

      @if (cartCount() > 0 && activeView() === 'menu') {
        <footer class="cart-bar">
          <div class="cart-info">
            <span class="cart-count">{{ cartCount() }} item{{ cartCount() === 1 ? '' : 's' }}</span>
            <span class="cart-total">{{ cartTotal() | number: '1.2-2' }}</span>
          </div>
          <button type="button" class="order-btn" [disabled]="placing()" (click)="placeOrder()">
            {{ placing() ? 'Sending…' : 'Place order' }}
          </button>
        </footer>
      }

      @if (op()) {
        <footer class="site-footer">
          <div class="pay">
            <span class="pay-label">Plată online securizată cu cardul prin</span>
            <!-- NETOPIA logo: the badge script (mny.ro/npId.js) is injected here at runtime. -->
            <div #netopiaSlot class="netopia"></div>
          </div>
          <div class="anpc">
            <a href="https://anpc.ro/ce-este-sal/" target="_blank" rel="noopener">ANPC – SAL</a>
            <a href="https://ec.europa.eu/consumers/odr" target="_blank" rel="noopener">ANPC – SOL</a>
          </div>
          <nav class="foot-legal">
            @for (link of legalLinks; track link.slug) {
              <a [routerLink]="['/legal', link.slug]">{{ link.label }}</a>
            }
          </nav>
          <div class="copy">© RENDEZVOUS EVENTS S.R.L. — CUI 41973877</div>
        </footer>
      }
    </main>

    @if (customerFormOpen()) {
      <div class="cust-modal-overlay" (click)="closeCustomerForm()">
        <div class="cust-modal" (click)="$event.stopPropagation()" role="dialog" aria-modal="true">
          @if (custStep() === 'phone') {
            <h3 class="cust-modal-title">Your phone number</h3>
            <p class="cust-modal-sub">We'll use it to notify you when your order is ready.</p>
            <div class="cust-phone-row">
              <select class="cust-input cust-prefix" [value]="custPrefix()" (change)="custPrefix.set($any($event.target).value)">
                @for (p of prefixes; track p) {
                  <option [value]="p">{{ p }}</option>
                }
              </select>
              <input class="cust-input" type="tel" inputmode="tel" placeholder="Phone number" autocomplete="tel-national"
                [value]="custPhone()" (input)="custPhone.set($any($event.target).value)" />
            </div>
            @if (custError()) {
              <p class="cust-err">{{ custError() }}</p>
            }
            <div class="cust-actions">
              <button type="button" class="cust-cancel" (click)="closeCustomerForm()">Cancel</button>
              <button type="button" class="cust-continue" [disabled]="custLooking()" (click)="submitPhone()">
                {{ custLooking() ? 'Checking…' : 'Continue' }}
              </button>
            </div>
          } @else {
            <h3 class="cust-modal-title">Your details</h3>
            <p class="cust-modal-sub">We don't have you yet — a few details to continue.</p>
            <input class="cust-input" type="text" placeholder="First name" autocomplete="given-name"
              [value]="custFirstName()" (input)="custFirstName.set($any($event.target).value)" />
            <input class="cust-input" type="text" placeholder="Last name" autocomplete="family-name"
              [value]="custLastName()" (input)="custLastName.set($any($event.target).value)" />
            <input class="cust-input" type="email" inputmode="email" placeholder="Email" autocomplete="email"
              [value]="custEmail()" (input)="custEmail.set($any($event.target).value)" />
            @if (custError()) {
              <p class="cust-err">{{ custError() }}</p>
            }
            <div class="cust-actions">
              <button type="button" class="cust-cancel" (click)="closeCustomerForm()">Cancel</button>
              <button type="button" class="cust-continue" [disabled]="placing()" (click)="submitDetails()">
                Continue to payment
              </button>
            </div>
          }
        </div>
      </div>
    }

    <!-- Recursive node: an orderable product renders as a row; a category renders a header + its children. -->
    <ng-template #nodeTpl let-node let-level="level">
      @if (node.orderable) {
        <div class="item-card">
          @if (node.imageObject && !transparentImages().has(node.imageObject)) {
            <img
              class="item-img"
              appTransparentCheck
              (transparent)="markTransparent(node.imageObject)"
              [src]="imageUrl(node.imageObject)"
              alt=""
            />
          } @else {
            <div class="item-img placeholder"></div>
          }
          <div class="item-body">
            <span class="item-name" [innerHTML]="node.name"></span>
          </div>
          <div class="item-side">
            @if (node.price != null) {
              <span class="item-price">{{ node.price | number: '1.2-2' }} RON</span>
            }
            <div class="qty">
              @if (qty(node.id) > 0) {
                <button type="button" class="qty-btn" aria-label="Remove one" (click)="dec(node.id)">−</button>
                <span class="qty-val">{{ qty(node.id) }}</span>
              }
              <button type="button" class="qty-btn" aria-label="Add one" (click)="inc(node)">+</button>
            </div>
          </div>
        </div>
      } @else {
        <section class="menu-cat" [id]="'cat-' + node.id">
          <div class="cat-head" [class.sub]="level > 0">
            <span class="cat-name" [innerHTML]="node.name"></span>
          </div>
          <div class="cat-children">
            @for (child of node.children; track child.id) {
              <ng-container *ngTemplateOutlet="nodeTpl; context: { $implicit: child, level: level + 1 }"></ng-container>
            }
          </div>
        </section>
      }
    </ng-template>
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
      background: #fff;
    }
    .topbar {
      position: sticky;
      top: 0;
      z-index: 10;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      height: 56px;
      padding: 0 1rem;
      background: #fff;
      border-bottom: 1px solid var(--border);
    }
    .brand {
      display: flex;
      align-items: center;
      min-width: 0;
    }
    .logo {
      width: 44px;
      height: 44px;
      object-fit: contain;
      padding: 4px;
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(18, 27, 46, 0.08);
    }
    .brand-name {
      font-weight: 800;
      color: var(--text);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .hamburger {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      color: var(--text);
      background: none;
      border: none;
      cursor: pointer;
    }
    .drawer-backdrop {
      position: fixed;
      inset: 0;
      z-index: 20;
      background: rgba(18, 27, 46, 0.35);
    }
    .drawer {
      position: fixed;
      top: 0;
      right: 0;
      bottom: 0;
      z-index: 21;
      display: flex;
      flex-direction: column;
      width: 240px;
      max-width: 80vw;
      padding: 4rem 0.75rem 1rem;
      background: #fff;
      box-shadow: -0.5rem 0 1.5rem rgba(18, 27, 46, 0.15);
      overflow-y: auto;
    }
    .drawer-close {
      position: absolute;
      top: 0.75rem;
      right: 0.75rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      color: var(--text);
      background: none;
      border: none;
      cursor: pointer;
    }
    .drawer-item {
      display: block;
      width: 100%;
      padding: 0.85rem 1rem;
      font: inherit;
      font-size: 1rem;
      font-weight: 600;
      text-align: left;
      color: var(--text);
      background: none;
      border: none;
      border-radius: 8px;
      cursor: pointer;
    }
    .drawer-item:hover,
    .drawer-item.active {
      background: var(--page-bg);
      color: var(--primary);
    }
    .drawer-legal {
      margin-top: auto;
      padding-top: 0.75rem;
      border-top: 1px solid var(--border);
    }
    .drawer-legal-item {
      display: block;
      padding: 0.55rem 1rem;
      font-size: 0.82rem;
      color: var(--muted);
      text-decoration: none;
    }
    .drawer-legal-item:hover {
      color: var(--primary);
    }
    .cust {
      max-width: 30rem;
      margin: 0 auto;
      padding: 2rem 1.25rem 6rem;
      text-align: center;
    }
    .state {
      margin: 3rem 0;
      color: var(--muted);
    }
    .state.err {
      color: var(--danger);
    }
    .cust-head {
      margin: 1.5rem 0 1rem;
    }
    .event {
      margin: 0;
      font-size: 0.85rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--muted);
    }
    .table {
      margin: 0.25rem 0 0;
      font-size: 2rem;
      font-weight: 800;
      color: var(--text);
    }
    .placed {
      margin: 0.75rem 0;
      padding: 0.6rem 0.85rem;
      font-size: 0.9rem;
      color: #1f7a3d;
      background: rgba(40, 167, 69, 0.1);
      border: 1px solid rgba(40, 167, 69, 0.25);
      border-radius: 8px;
    }
    .soon {
      color: var(--muted);
      font-size: 0.9rem;
      text-align: center;
    }
    /* servio-style: horizontal category quick-nav (jump to section) */
    .cat-nav {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      margin: 0.25rem 0 1rem;
      padding-bottom: 4px;
      scrollbar-width: none;
    }
    .cat-nav::-webkit-scrollbar {
      display: none;
    }
    .cat-chip {
      flex-shrink: 0;
      padding: 7px 14px;
      font: inherit;
      font-size: 13px;
      font-weight: 600;
      color: var(--text);
      white-space: nowrap;
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 999px;
      cursor: pointer;
    }
    /* servio-style: single scrolling list, products one per row */
    .menu-tree {
      display: flex;
      flex-direction: column;
      gap: 0;
      text-align: left;
    }
    .menu-cat {
      scroll-margin-top: 72px;
    }
    .cat-children {
      display: flex;
      flex-direction: column;
      gap: 0;
    }
    .cat-head {
      padding: 28px 0 8px;
    }
    .cat-head.sub {
      padding-top: 12px;
    }
    .cat-name {
      display: block;
      font-size: 14px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--text);
    }
    .cat-head.sub .cat-name {
      font-size: 12px;
      font-weight: 600;
      text-transform: none;
      letter-spacing: 0;
      color: var(--muted);
    }
    .item-card {
      display: flex;
      align-items: stretch;
      gap: 12px;
      min-height: 92px;
      padding: 14px 2px;
      background: #fff;
      border-bottom: 1px solid var(--border);
    }
    .item-img {
      flex-shrink: 0;
      align-self: center;
      width: 64px;
      height: 64px;
      object-fit: cover;
      border-radius: 8px;
    }
    .item-img.placeholder {
      background: var(--page-bg);
      border: 1px dashed var(--border);
    }
    .item-body {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .item-name {
      font-size: 14px;
      font-weight: 600;
      line-height: 1.25;
      color: var(--text);
    }
    .item-price {
      font-size: 12px;
      font-weight: 700;
      color: var(--muted);
      font-variant-numeric: tabular-nums;
    }
    .item-side {
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 8px;
    }
    .qty {
      margin-top: auto;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .qty-btn {
      width: 24px;
      height: 24px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      font-weight: 700;
      line-height: 1;
      color: var(--primary);
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 50%;
      cursor: pointer;
    }
    .qty-val {
      min-width: 18px;
      font-size: 14px;
      font-weight: 700;
      text-align: center;
      color: var(--text);
      font-variant-numeric: tabular-nums;
    }
    .cart-bar {
      position: fixed;
      left: 0;
      right: 0;
      bottom: 0;
      max-width: 30rem;
      margin: 0 auto;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.75rem 1.25rem;
      background: #fff;
      border-top: 1px solid var(--border);
      box-shadow: 0 -0.5rem 1rem rgba(18, 27, 46, 0.08);
    }
    .site-footer {
      margin: 2rem -1rem 0;
      padding: 1.5rem 1rem calc(1.25rem + env(safe-area-inset-bottom));
      background: #ffffff;
      border-top: 1px solid var(--border);
      text-align: center;
    }
    .site-footer .pay {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
    }
    .site-footer .pay-label {
      font-size: 0.78rem;
      color: var(--muted);
    }
    .site-footer .netopia {
      font-weight: 700;
      color: var(--text);
      text-decoration: none;
    }
    .site-footer .anpc {
      display: flex;
      justify-content: center;
      gap: 1rem;
      margin: 1rem 0 0.5rem;
    }
    .site-footer .anpc a {
      font-size: 0.8rem;
      font-weight: 600;
      color: var(--primary);
      text-decoration: none;
    }
    .site-footer .foot-legal {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 0.35rem 0.9rem;
      margin-top: 0.75rem;
      padding-top: 0.75rem;
      border-top: 1px solid var(--border);
    }
    .site-footer .foot-legal a {
      font-size: 0.75rem;
      color: var(--muted);
      text-decoration: none;
    }
    .site-footer .foot-legal a:hover {
      color: var(--primary);
    }
    .site-footer .copy {
      margin-top: 0.75rem;
      font-size: 0.72rem;
      color: var(--muted);
    }
    .cart-info {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
    }
    .cart-count {
      font-size: 0.78rem;
      color: var(--muted);
    }
    .cart-total {
      font-size: 1.1rem;
      font-weight: 800;
      font-variant-numeric: tabular-nums;
      color: var(--text);
    }
    .order-btn {
      padding: 0.7rem 1.5rem;
      font: inherit;
      font-weight: 700;
      color: var(--primary);
      background: #fff;
      border: 1px solid var(--primary);
      border-radius: 8px;
      cursor: pointer;
    }
    .order-btn:disabled {
      opacity: 0.6;
      cursor: default;
    }
    /* customer-details modal (pay-now, first-time customer) */
    .cust-modal-overlay {
      position: fixed;
      inset: 0;
      z-index: 40;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.25rem;
      background: rgba(18, 27, 46, 0.35);
    }
    .cust-modal {
      width: 100%;
      max-width: 22rem;
      display: flex;
      flex-direction: column;
      gap: 0.6rem;
      padding: 1.25rem;
      background: #fff;
      border-radius: 14px;
      box-shadow: 0 1rem 2rem rgba(18, 27, 46, 0.25);
    }
    .cust-modal-title {
      margin: 0;
      font-size: 1.2rem;
      color: var(--text);
    }
    .cust-modal-sub {
      margin: 0 0 0.25rem;
      font-size: 0.85rem;
      color: var(--muted);
    }
    .cust-input {
      width: 100%;
      padding: 0.7rem 0.8rem;
      font: inherit;
      color: var(--text);
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 8px;
    }
    .cust-input:focus {
      outline: none;
      border-color: var(--primary);
    }
    .cust-phone-row {
      display: flex;
      gap: 0.5rem;
    }
    .cust-prefix {
      flex: 0 0 5.5rem;
    }
    .cust-err {
      margin: 0;
      font-size: 0.82rem;
      color: var(--danger);
    }
    .cust-actions {
      display: flex;
      gap: 0.5rem;
      margin-top: 0.5rem;
    }
    .cust-cancel,
    .cust-continue {
      flex: 1;
      padding: 0.7rem;
      font: inherit;
      font-weight: 700;
      border-radius: 8px;
      cursor: pointer;
    }
    .cust-cancel {
      color: var(--text);
      background: #fff;
      border: 1px solid var(--border);
    }
    .cust-continue {
      color: #fff;
      background: var(--primary);
      border: 1px solid var(--primary);
    }
    .cust-continue:disabled {
      opacity: 0.6;
      cursor: default;
    }
    /* order history (Orders drawer view) */
    .orders-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      text-align: left;
    }
    .ord-card {
      padding: 0.85rem 1rem;
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 12px;
    }
    .ord-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .ord-no {
      font-weight: 700;
      color: var(--text);
    }
    .ord-status {
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      padding: 0.2rem 0.55rem;
      border-radius: 999px;
      color: var(--muted);
      background: var(--page-bg);
    }
    .ord-status.st-ordered { color: #1d4ed8; background: rgba(52, 84, 209, 0.12); }
    .ord-status.st-ready { color: #1f7a3d; background: rgba(40, 167, 69, 0.14); }
    .ord-status.st-delivered { color: var(--muted); background: var(--page-bg); }
    .ord-status.st-canceled,
    .ord-status.st-cancelled { color: var(--danger); background: rgba(220, 53, 69, 0.12); }
    .ord-when {
      margin-top: 0.15rem;
      font-size: 0.78rem;
      color: var(--muted);
    }
    .ord-items {
      margin: 0.6rem 0;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .ord-line {
      display: flex;
      justify-content: space-between;
      gap: 0.75rem;
      font-size: 0.9rem;
      color: var(--text);
    }
    .ord-line-amt {
      font-variant-numeric: tabular-nums;
      color: var(--muted);
    }
    .ord-total {
      display: flex;
      justify-content: space-between;
      padding-top: 0.5rem;
      border-top: 1px solid var(--border);
      font-weight: 700;
      color: var(--text);
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class CustomerOrderPointPage implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(CustomerOrderPointService);
  private readonly opId = this.route.snapshot.paramMap.get('opId') ?? '';

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly op = signal<CustomerOrderPoint | null>(null);

  // app bar / drawer
  readonly menuOpen = signal(false);
  readonly logoFailed = signal(false);
  readonly legalLinks = LEGAL_LINKS;

  // NETOPIA payment-logo badge (Identitate vizuală). The vendor script injects the logo right
  // after itself, so we append it into this slot once the footer renders.
  private readonly netopiaSlot = viewChild<ElementRef<HTMLDivElement>>('netopiaSlot');
  private netopiaInjected = false;

  // cart: product id -> quantity
  readonly cart = signal<Record<string, number>>({});
  readonly placing = signal(false);
  readonly placed = signal(false);

  // customer identity for the pay-now (Netopia) flow
  private readonly CUSTOMER_KEY = 'yammer.customerId';
  private readonly EVENT_KEY = 'yammer.eventId';
  readonly prefixes = ['+40', '+44', '+1', '+49', '+33', '+39', '+34', '+31', '+30', '+48', '+359', '+36'];
  readonly customerFormOpen = signal(false);
  readonly custStep = signal<'phone' | 'details'>('phone');
  readonly custPrefix = signal('+40');
  readonly custPhone = signal('');
  readonly custFirstName = signal('');
  readonly custLastName = signal('');
  readonly custEmail = signal('');
  readonly custError = signal<string | null>(null);
  readonly custLooking = signal(false);

  // which view the drawer selected; "orders" requires a known customer + event
  readonly activeView = signal<'menu' | 'orders'>('menu');
  readonly customerId = signal<string | null>(null);
  // event the customer is at — inferred from the scanned order point and persisted
  readonly eventId = signal<string | null>(null);
  readonly hasOrdersTab = computed(() => !!this.customerId() && !!this.eventId());
  readonly custOrders = signal<CustomerOrder[]>([]);
  readonly ordersLoading = signal(false);

  // top-level categories, surfaced as quick-nav chips that scroll to each section
  readonly topCategories = computed<MenuNode[]>(() =>
    (this.op()?.menu ?? []).filter((n) => !n.orderable),
  );

  // image objects detected to have a transparent background — hidden, placeholder shown instead
  readonly transparentImages = signal<Set<string>>(new Set());
  markTransparent(object: string): void {
    this.transparentImages.update((s) => new Set(s).add(object));
  }

  /** Flat list of orderable products in the menu, for cart totals / price lookup. */
  private readonly products = computed(() => {
    const out: MenuNode[] = [];
    const walk = (nodes: MenuNode[]) => {
      for (const n of nodes) {
        if (n.orderable) out.push(n);
        if (n.children?.length) walk(n.children);
      }
    };
    walk(this.op()?.menu ?? []);
    return out;
  });
  private readonly priceById = computed(() => {
    const m = new Map<string, number>();
    for (const p of this.products()) m.set(p.id, p.price ?? 0);
    return m;
  });

  readonly cartCount = computed(() =>
    Object.values(this.cart()).reduce((s, q) => s + q, 0),
  );
  readonly cartTotal = computed(() => {
    const price = this.priceById();
    return Object.entries(this.cart()).reduce(
      (s, [id, q]) => s + (price.get(id) ?? 0) * q,
      0,
    );
  });

  private readonly cartKey = `yammer.cart.${this.opId}`;

  constructor() {
    // The customer page is full-white (no admin gray peeking through on mobile overscroll).
    document.body.style.background = '#fff';
    this.customerId.set(localStorage.getItem(this.CUSTOMER_KEY));
    this.eventId.set(localStorage.getItem(this.EVENT_KEY));

    // The view (menu / orders) comes from the route, so a refresh stays on the same view.
    this.activeView.set(this.route.snapshot.data['view'] === 'orders' ? 'orders' : 'menu');

    // Persist the cart per order point so toggling views / refreshing keeps it.
    const savedCart = sessionStorage.getItem(this.cartKey);
    if (savedCart) {
      try {
        this.cart.set(JSON.parse(savedCart));
      } catch {
        /* ignore corrupt cart */
      }
    }
    effect(() => sessionStorage.setItem(this.cartKey, JSON.stringify(this.cart())));

    // Inject the NETOPIA logo badge once its footer slot exists.
    effect(() => {
      const slot = this.netopiaSlot();
      if (!slot || this.netopiaInjected) {
        return;
      }
      this.netopiaInjected = true;
      const s = document.createElement('script');
      s.src = 'https://mny.ro/npId.js?p=165091';
      s.type = 'text/javascript';
      s.setAttribute('data-version', 'orizontal');
      s.setAttribute('data-contrast-color', '#ffffff');
      slot.nativeElement.appendChild(s);
    });

    if (!this.opId) {
      this.error.set('Invalid link.');
      this.loading.set(false);
      return;
    }
    if (this.activeView() === 'orders') {
      this.loadCustomerOrders();
    }
    this.service.getOrderPoint(this.opId).subscribe({
      next: (op) => {
        this.op.set(op);
        // Infer + persist the event from the scanned order point (for the Orders history scope).
        if (op.eventId) {
          this.eventId.set(op.eventId);
          localStorage.setItem(this.EVENT_KEY, op.eventId);
        }
        this.loading.set(false);
      },
      error: () => {
        this.error.set('This table could not be found.');
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    document.body.style.background = '';
  }

  imageUrl(object: string): string {
    return this.service.imageUrl(object);
  }

  /** Smooth-scroll the menu to a top-level category section. */
  scrollTo(categoryId: string): void {
    document
      .getElementById('cat-' + categoryId)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /** Brand logo: the client's logo, falling back to the app placeholder when absent/unloadable. */
  logoSrc(): string {
    const clientId = this.op()?.clientId;
    if (!clientId || this.logoFailed()) return 'assets/images/logo-abbr.png';
    return this.service.clientLogoUrl(clientId);
  }

  toggleMenu(): void {
    this.menuOpen.update((o) => !o);
  }
  closeMenu(): void {
    this.menuOpen.set(false);
  }
  showMenu(): void {
    this.closeMenu();
    this.router.navigate(['/customer/order-point', this.opId]);
  }
  showOrders(): void {
    this.closeMenu();
    this.router.navigate(['/customer/order-point', this.opId, 'orders']);
  }
  private loadCustomerOrders(): void {
    const customerId = this.customerId();
    const eventId = this.eventId();
    if (!customerId || !eventId) return;
    this.ordersLoading.set(true);
    this.service.customerOrders(customerId, eventId).subscribe({
      next: (orders) => {
        this.custOrders.set(orders);
        this.ordersLoading.set(false);
      },
      error: () => this.ordersLoading.set(false),
    });
  }

  /** Relative time: "x min ago" / "x hours ago" / "dd.MM.YYYY". */
  relativeTime(iso: string): string {
    return timeAgo(iso);
  }

  qty(id: string): number {
    return this.cart()[id] ?? 0;
  }
  inc(n: MenuNode): void {
    this.placed.set(false);
    this.cart.update((c) => ({ ...c, [n.id]: (c[n.id] ?? 0) + 1 }));
  }
  dec(id: string): void {
    this.cart.update((c) => {
      const next = { ...c };
      const q = (next[id] ?? 0) - 1;
      if (q <= 0) delete next[id];
      else next[id] = q;
      return next;
    });
  }

  placeOrder(): void {
    if (this.placing() || this.cartCount() === 0) return;
    const o = this.op();
    // Pay-now and we don't yet know this customer → look them up / collect details before the gateway.
    if (o && !o.payLater && !localStorage.getItem(this.CUSTOMER_KEY)) {
      this.openCustomerForm();
      return;
    }
    // Returning pay-now customer → send the stored id; pay-later sends nothing.
    const storedId = localStorage.getItem(this.CUSTOMER_KEY);
    const customer = o && !o.payLater && storedId ? { id: storedId } : undefined;
    this.submitOrder(customer);
  }

  private openCustomerForm(): void {
    this.custStep.set('phone');
    this.custPhone.set('');
    this.custFirstName.set('');
    this.custLastName.set('');
    this.custEmail.set('');
    this.custError.set(null);
    this.customerFormOpen.set(true);
  }

  closeCustomerForm(): void {
    this.customerFormOpen.set(false);
  }

  /** Step 1: look the customer up by prefix + phone. Found → pay; not found → ask for details. */
  submitPhone(): void {
    const phone = this.custPhone().trim();
    if (!phone) {
      this.custError.set('Please enter your phone number.');
      return;
    }
    this.custError.set(null);
    this.custLooking.set(true);
    this.service.lookupCustomer(this.custPrefix(), phone).subscribe({
      next: (res) => {
        this.custLooking.set(false);
        if (res.customerId) {
          localStorage.setItem(this.CUSTOMER_KEY, res.customerId);
          this.customerId.set(res.customerId);
          this.customerFormOpen.set(false);
          this.submitOrder({ id: res.customerId });
        } else {
          this.custStep.set('details'); // unknown customer → collect name + email
        }
      },
      error: () => {
        this.custLooking.set(false);
        this.custError.set('Could not check your number. Please try again.');
      },
    });
  }

  /** Step 2: a first-time customer supplies name + email, then continues to payment. */
  submitDetails(): void {
    const firstName = this.custFirstName().trim();
    const lastName = this.custLastName().trim();
    const email = this.custEmail().trim();
    if (!firstName || !lastName || !email) {
      this.custError.set('Please fill in all fields.');
      return;
    }
    this.customerFormOpen.set(false);
    this.submitOrder({
      prefix: this.custPrefix(),
      phone: this.custPhone().trim(),
      firstName,
      lastName,
      email,
    });
  }

  private submitOrder(customer?: CustomerInfo): void {
    const items = Object.entries(this.cart()).map(([menuItemId, quantity]) => ({ menuItemId, quantity }));
    const returnUrl = `${window.location.origin}/customer/order-point/${this.opId}/payment-return`;
    this.placing.set(true);
    this.error.set(null);
    this.service.placeOrder(this.opId, items, returnUrl, customer).subscribe({
      next: (result) => {
        if (result.customerId) {
          localStorage.setItem(this.CUSTOMER_KEY, result.customerId);
          this.customerId.set(result.customerId);
        }
        if (result.paymentUrl) {
          // Pay-now: order handed to the gateway — clear the (persisted) cart, then navigate away.
          this.cart.set({});
          sessionStorage.removeItem(this.cartKey);
          window.location.href = result.paymentUrl;
          return;
        }
        // Pay-later: order created.
        this.cart.set({});
        this.placing.set(false);
        this.placed.set(true);
      },
      error: () => {
        this.placing.set(false);
        this.error.set('Could not place your order. Please try again.');
      },
    });
  }
}
