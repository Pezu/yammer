import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Slug + label for each legal page; also drives the hamburger drawer's legal section. */
export const LEGAL_LINKS: { slug: string; label: string }[] = [
  { slug: 'termeni', label: 'Termeni și condiții' },
  { slug: 'confidentialitate', label: 'Politica de confidențialitate' },
  { slug: 'livrare', label: 'Politica de livrare' },
  { slug: 'anulare', label: 'Politica de anulare și retur' },
  { slug: 'gdpr', label: 'Politica GDPR' },
  { slug: 'contact', label: 'Date de contact' },
];

/**
 * Site-wide compliance footer (required for the Netopia card-payments account): the NETOPIA payment
 * logo, ANPC SAL/SOL links, the legal-page links and the company line. Shown on every page.
 * `margin-top: auto` on the host pins it to the bottom of a flex-column page.
 */
@Component({
  selector: 'app-site-footer',
  imports: [RouterLink],
  template: `
    <footer class="site-footer">
      <div class="pay">
        <span class="pay-label">Plată securizată prin</span>
        <!-- NETOPIA logo (Identitate vizuală, POS 165091, horizontal, light bg). -->
        <a class="netopia" href="https://netopia-payments.com/" target="_blank" rel="noopener">
          <img src="https://mny.ro/np-black-0.svg" alt="NETOPIA Payments" title="NETOPIA Payments" />
        </a>
        <span class="dot">·</span>
        <a class="anpc-link" href="https://anpc.ro/ce-este-sal/" target="_blank" rel="noopener">ANPC SAL</a>
        <a class="anpc-link" href="https://ec.europa.eu/consumers/odr" target="_blank" rel="noopener">ANPC SOL</a>
      </div>
      <nav class="foot-legal">
        @for (link of legalLinks; track link.slug) {
          <a [routerLink]="['/legal', link.slug]">{{ link.label }}</a>
        }
      </nav>
      <div class="copy">© RENDEZVOUS EVENTS S.R.L. — CUI 41973877</div>
    </footer>
  `,
  styles: [
    `
      :host {
        display: block;
        margin-top: auto;
      }
      .site-footer {
        padding: 0.5rem 1rem calc(0.5rem + env(safe-area-inset-bottom));
        background: #ffffff;
        border-top: 1px solid var(--border);
        text-align: center;
      }
      .pay {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: center;
        gap: 0.3rem 0.45rem;
      }
      .pay-label {
        font-size: 0.62rem;
        color: var(--muted);
      }
      .netopia {
        display: inline-flex;
      }
      .netopia img {
        width: auto;
        height: 15px;
        max-width: 120px;
      }
      .dot {
        color: var(--border);
      }
      .anpc-link {
        font-size: 0.62rem;
        font-weight: 600;
        color: var(--primary);
        text-decoration: none;
      }
      .foot-legal {
        display: flex;
        flex-wrap: wrap;
        justify-content: center;
        gap: 0 0.5rem;
        margin-top: 0.2rem;
        padding-top: 0.2rem;
        border-top: 1px solid var(--border);
      }
      .foot-legal a {
        font-size: 0.52rem;
        line-height: 1.4;
        color: var(--muted);
        text-decoration: none;
      }
      .foot-legal a:hover {
        color: var(--primary);
      }
      .copy {
        margin-top: 0.16rem;
        font-size: 0.48rem;
        color: var(--muted);
      }
    `,
  ],
})
export class SiteFooter {
  readonly legalLinks = LEGAL_LINKS;
}
