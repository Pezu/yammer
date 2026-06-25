import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

/**
 * Public legal/policy pages required for the Netopia (card payments) production account:
 * Terms, Privacy, Delivery, Cancellation, GDPR and company contact details. Reached at
 * `/legal/:doc`; the content is static Romanian text (one lazy chunk for all docs).
 *
 * Company-specific fields are marked with [PLACEHOLDER] tokens — replace them with the
 * real company data before submitting to Netopia.
 */
@Component({
  selector: 'app-legal-page',
  template: `
    <header class="topbar">
      <button type="button" class="back" (click)="goBack()" aria-label="Înapoi">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline></svg>
      </button>
      <span class="page-title">{{ pageTitle() }}</span>
    </header>

    <main class="legal">
      @if (doc(); as d) {
        <h1>{{ d.title }}</h1>
        <div class="body" [innerHTML]="d.html"></div>
      } @else {
        <p class="state">Pagina nu a fost găsită.</p>
      }
    </main>
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
        min-height: 100dvh;
        background: var(--page-bg);
      }
      .topbar {
        position: sticky;
        top: 0;
        z-index: 10;
        display: flex;
        align-items: center;
        gap: 0.5rem;
        height: 56px;
        padding: 0 0.75rem;
        background: #fff;
        border-bottom: 1px solid var(--border);
      }
      .back {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: 40px;
        color: var(--text);
        background: none;
        border: none;
        border-radius: 8px;
        cursor: pointer;
      }
      .page-title {
        font-weight: 700;
        font-size: 1rem;
        color: var(--text);
      }
      .legal {
        max-width: 720px;
        margin: 0 auto;
        padding: 1.25rem 1.1rem 3rem;
        color: var(--text);
        line-height: 1.6;
      }
      .legal h1 {
        font-size: 1.4rem;
        margin: 0 0 1rem;
      }
      .body :is(h2) {
        font-size: 1.05rem;
        margin: 1.5rem 0 0.4rem;
      }
      .body p,
      .body li {
        color: var(--text);
        font-size: 0.95rem;
      }
      .body ul {
        padding-left: 1.2rem;
        margin: 0.4rem 0;
      }
      .body a {
        color: var(--primary);
      }
      .state {
        padding: 2rem 1rem;
        color: var(--muted);
      }
    `,
  ],
})
export class LegalPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly key = toSignal(
    this.route.paramMap.pipe(map((p) => p.get('doc') ?? '')),
    { initialValue: this.route.snapshot.paramMap.get('doc') ?? '' },
  );

  readonly doc = computed(() => LEGAL_DOCS[this.key()] ?? null);
  readonly pageTitle = computed(() => this.doc()?.title ?? 'Informații legale');

  goBack(): void {
    if (window.history.length > 1) {
      window.history.back();
    } else {
      this.router.navigateByUrl('/');
    }
  }
}

/** Ordered list used to render the drawer's legal section (slug + label). */
export const LEGAL_LINKS: { slug: string; label: string }[] = [
  { slug: 'termeni', label: 'Termeni și condiții' },
  { slug: 'confidentialitate', label: 'Politica de confidențialitate' },
  { slug: 'livrare', label: 'Politica de livrare' },
  { slug: 'anulare', label: 'Politica de anulare și retur' },
  { slug: 'gdpr', label: 'Politica GDPR' },
  { slug: 'contact', label: 'Date de contact' },
];

interface LegalDoc {
  title: string;
  html: string;
}

const COMPANY = `
  <p>
    <strong>[NUME COMPANIE] S.R.L.</strong><br />
    CUI: 41973877 &nbsp;|&nbsp; Cod TVA: RO41973877<br />
    Nr. Reg. Com.: J2019003544080<br />
    Sediu social: Strada Basarabia, Nr. 12, Et. Parter, Ap. 4, Brașov, Județ Brașov, România<br />
    Telefon: <a href="tel:[TELEFON]">[TELEFON]</a><br />
    E-mail: <a href="mailto:[EMAIL]">[EMAIL]</a>
  </p>
`;

const LEGAL_DOCS: Record<string, LegalDoc> = {
  contact: {
    title: 'Date de contact',
    html: `
      <p>Datele de identificare și de contact ale comerciantului:</p>
      ${COMPANY}
      <h2>Obiect de activitate</h2>
      <p>Cod CAEN principal: 9329 — Alte activități recreative și distractive n.c.a.</p>
      <h2>Program</h2>
      <p>[PROGRAM DE LUCRU — ex. Luni–Duminică, 10:00–23:00]</p>
      <h2>Plăți online</h2>
      <p>
        Plățile cu cardul sunt procesate prin <strong>NETOPIA Payments</strong>. [NUME COMPANIE]
        nu stochează datele cardului dumneavoastră; acestea sunt procesate în mod securizat de
        către procesatorul de plăți, conform standardului PCI-DSS.
      </p>
    `,
  },
  termeni: {
    title: 'Termeni și condiții',
    html: `
      <p>
        Acești termeni și condiții reglementează utilizarea platformei de comandă online operate de
        [NUME COMPANIE] S.R.L. Prin plasarea unei comenzi, confirmați că ați citit și acceptat
        prezentele condiții.
      </p>
      <h2>1. Comerciant</h2>
      ${COMPANY}
      <h2>2. Produse și prețuri</h2>
      <p>
        Produsele disponibile, descrierile și prețurile sunt afișate în meniul aferent fiecărui punct
        de comandă (masă). Toate prețurile sunt exprimate în <strong>lei (RON)</strong> și includ TVA.
        Ne rezervăm dreptul de a modifica meniul și prețurile în orice moment.
      </p>
      <h2>3. Plasarea comenzii</h2>
      <p>
        Comanda se plasează prin scanarea codului QR de la masă și selectarea produselor dorite. În
        funcție de punctul de comandă, plata se face fie ulterior la unitate, fie online cu cardul prin
        NETOPIA Payments înainte de confirmarea comenzii.
      </p>
      <h2>4. Plata online</h2>
      <p>
        Plățile cu cardul sunt procesate de NETOPIA Payments. Nu colectăm și nu stocăm datele cardului
        dumneavoastră. Tranzacțiile sunt securizate prin conexiune criptată (SSL) și conforme PCI-DSS.
      </p>
      <h2>5. Livrare</h2>
      <p>
        Produsele sunt servite la masa de la care a fost plasată comanda, în incinta unității. A se
        vedea <a href="/legal/livrare">Politica de livrare</a>.
      </p>
      <h2>6. Anulare și retur</h2>
      <p>A se vedea <a href="/legal/anulare">Politica de anulare și retur</a>.</p>
      <h2>7. Protecția datelor</h2>
      <p>
        Prelucrarea datelor cu caracter personal este descrisă în
        <a href="/legal/confidentialitate">Politica de confidențialitate</a> și
        <a href="/legal/gdpr">Politica GDPR</a>.
      </p>
      <h2>8. Legislație aplicabilă</h2>
      <p>
        Prezentele condiții sunt guvernate de legislația română. Eventualele litigii se soluționează pe
        cale amiabilă sau, în lipsa unei înțelegeri, de instanțele competente din România. Consumatorii
        pot folosi și platforma SOL (Soluționarea Online a Litigiilor):
        <a href="https://ec.europa.eu/consumers/odr" target="_blank" rel="noopener">ec.europa.eu/consumers/odr</a>.
      </p>
    `,
  },
  confidentialitate: {
    title: 'Politica de confidențialitate',
    html: `
      <p>
        [NUME COMPANIE] S.R.L. respectă confidențialitatea datelor dumneavoastră. Această politică
        explică ce date colectăm, în ce scop și cum le protejăm.
      </p>
      <h2>Date colectate</h2>
      <ul>
        <li>Nume și prenume (atunci când le furnizați la plasarea comenzii);</li>
        <li>Număr de telefon (pentru a vă notifica atunci când comanda este gata);</li>
        <li>Adresă de e-mail (opțional);</li>
        <li>Detaliile comenzii (produse, sumă, masă/punct de comandă, dată).</li>
      </ul>
      <p>
        <strong>Nu colectăm și nu stocăm datele cardului bancar.</strong> Acestea sunt introduse și
        procesate exclusiv de NETOPIA Payments.
      </p>
      <h2>Scopul prelucrării</h2>
      <ul>
        <li>Procesarea și onorarea comenzilor;</li>
        <li>Notificarea privind statusul comenzii;</li>
        <li>Procesarea plăților online;</li>
        <li>Îndeplinirea obligațiilor legale (fiscale, contabile).</li>
      </ul>
      <h2>Partajarea datelor</h2>
      <p>
        Datele pot fi partajate cu procesatorul de plăți (NETOPIA Payments) și cu autoritățile, atunci
        când legea impune. Nu vindem datele dumneavoastră către terți.
      </p>
      <h2>Drepturile dumneavoastră</h2>
      <p>
        Aveți dreptul de acces, rectificare, ștergere, restricționare și opoziție. Pentru exercitarea
        acestor drepturi ne puteți contacta la <a href="mailto:[EMAIL]">[EMAIL]</a>. Detalii suplimentare
        în <a href="/legal/gdpr">Politica GDPR</a>.
      </p>
    `,
  },
  livrare: {
    title: 'Politica de livrare',
    html: `
      <p>
        Platforma este destinată comenzilor plasate <strong>în incinta unității</strong>, prin scanarea
        codului QR de la masă.
      </p>
      <h2>Modalitate de livrare</h2>
      <p>
        Produsele comandate sunt preparate și servite direct la masa de la care a fost plasată comanda,
        de către personalul unității.
      </p>
      <h2>Termen de livrare</h2>
      <p>
        Comenzile sunt onorate în cel mai scurt timp posibil, în ordinea primirii. Timpul de așteptare
        depinde de gradul de ocupare și de specificul produselor comandate.
      </p>
      <h2>Costuri de livrare</h2>
      <p>Nu se percep costuri suplimentare de livrare pentru servirea la masă.</p>
    `,
  },
  anulare: {
    title: 'Politica de anulare și retur',
    html: `
      <p>
        Având în vedere natura perisabilă a produselor alimentare și a băuturilor preparate la comandă,
        se aplică următoarele reguli.
      </p>
      <h2>Anularea comenzii</h2>
      <p>
        O comandă poate fi anulată doar înainte de a începe prepararea acesteia. Pentru anulare,
        adresați-vă personalului unității sau contactați-ne la <a href="tel:[TELEFON]">[TELEFON]</a>.
      </p>
      <h2>Retur și rambursare</h2>
      <p>
        Conform legislației (O.U.G. 34/2014, art. 16), dreptul de retragere nu se aplică produselor
        alimentare și băuturilor preparate, perisabile. Dacă un produs prezintă neconformități, vă rugăm
        să sesizați imediat personalul; vom înlocui produsul sau vom rambursa contravaloarea acestuia.
      </p>
      <h2>Rambursarea plăților online</h2>
      <p>
        În cazul plăților online aprobate pentru rambursare, suma se returnează pe același card utilizat
        la plată, prin NETOPIA Payments, în termen de maximum 14 zile.
      </p>
    `,
  },
  gdpr: {
    title: 'Politica GDPR',
    html: `
      <p>
        Prelucrarea datelor cu caracter personal se realizează în conformitate cu Regulamentul (UE)
        2016/679 (GDPR).
      </p>
      <h2>Operator de date</h2>
      ${COMPANY}
      <h2>Temeiul legal</h2>
      <ul>
        <li>Executarea contractului (procesarea comenzii și a plății);</li>
        <li>Îndeplinirea obligațiilor legale (fiscale, contabile);</li>
        <li>Consimțământul dumneavoastră, acolo unde este cazul.</li>
      </ul>
      <h2>Durata stocării</h2>
      <p>
        Datele sunt păstrate pe durata necesară îndeplinirii scopurilor și a obligațiilor legale (de
        regulă, documentele fiscale se păstrează conform termenelor legale).
      </p>
      <h2>Securitatea datelor</h2>
      <p>
        Aplicăm măsuri tehnice și organizatorice adecvate pentru protejarea datelor. Conexiunea este
        criptată (SSL/HTTPS), iar datele cardului sunt procesate exclusiv de NETOPIA Payments (PCI-DSS).
      </p>
      <h2>Drepturile persoanei vizate</h2>
      <p>
        Acces, rectificare, ștergere, restricționare, portabilitate și opoziție. Pentru exercitare:
        <a href="mailto:[EMAIL]">[EMAIL]</a>. Aveți, de asemenea, dreptul de a depune o plângere la
        Autoritatea Națională de Supraveghere a Prelucrării Datelor cu Caracter Personal (ANSPDCP),
        <a href="https://www.dataprotection.ro" target="_blank" rel="noopener">dataprotection.ro</a>.
      </p>
    `,
  },
};
