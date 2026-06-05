import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-confirm-dialog',
  template: `
    <div class="overlay" (click)="cancelled.emit()">
      <div class="dialog" (click)="$event.stopPropagation()" role="dialog" aria-modal="true">
        <h3 class="title">{{ title() }}</h3>
        <p class="message">{{ message() }}</p>
        <div class="footer">
          <button type="button" class="btn ghost" (click)="cancelled.emit()">
            {{ cancelLabel() }}
          </button>
          <button type="button" class="btn" [class.danger]="danger()" (click)="confirmed.emit()">
            {{ confirmLabel() }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .overlay {
      position: fixed;
      inset: 0;
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      background: rgba(18, 27, 46, 0.45);
      animation: fade 0.15s ease-out;
    }
    .dialog {
      width: 100%;
      max-width: 400px;
      padding: 1.75rem;
      background: #fff;
      border-radius: 10px;
      box-shadow: 0 1rem 3rem rgba(18, 27, 46, 0.25);
      animation: pop 0.15s ease-out;
    }
    .title {
      margin: 0 0 0.6rem;
      font-size: 1.15rem;
      font-weight: 800;
      color: var(--text);
    }
    .message {
      margin: 0 0 1.5rem;
      font-size: 0.9rem;
      color: var(--muted);
    }
    .footer {
      display: flex;
      justify-content: flex-end;
      gap: 0.6rem;
    }
    .btn {
      padding: 0.5rem 1.1rem;
      font-size: 0.9rem;
      font-weight: 600;
      color: #fff;
      background: var(--primary);
      border: none;
      border-radius: 6px;
      cursor: pointer;
    }
    .btn.ghost {
      color: var(--text);
      background: #fff;
      border: 1px solid var(--border);
    }
    .btn.danger {
      background: var(--danger);
    }
    @keyframes fade {
      from { opacity: 0; }
    }
    @keyframes pop {
      from { opacity: 0; transform: translateY(-8px) scale(0.98); }
    }
  `,
})
export class ConfirmDialog {
  readonly title = input('Confirm');
  readonly message = input('');
  readonly confirmLabel = input('Confirm');
  readonly cancelLabel = input('Cancel');
  readonly danger = input(false);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();
}
