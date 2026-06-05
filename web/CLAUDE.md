# Yammer Web

Angular frontend for the Yammer project. **Lightweight by design** — no CSS
framework, no UI library, no web fonts. Screens are visually modelled on the
**Duralux** admin template, but the markup and styles are hand-written so we ship
only what each page needs.

## Stack

- **Angular 21** (standalone components, signals, no NgModules)
- **Zoneless** change detection — there is no `zone.js` dependency (Angular 21
  default). Don't add it back.
- **TypeScript**, **SCSS**
- **No Bootstrap / no icon fonts.** Styling is bespoke SCSS: a small global
  `src/styles.scss` (reset + CSS custom-property theme tokens) plus
  component-scoped styles. System font stack, no Google Fonts.
- Reactive Forms for inputs; `HttpClient` (fetch backend) for API calls

## Weight

A production build (`npm run build`) is ~70 kB transfer total (Angular runtime +
router + forms + http, gzipped) plus a ~400-byte global stylesheet. Keep it that
way: prefer plain CSS/SCSS over pulling in component libraries, and only add a
dependency when it clearly pays for its bytes.

## Commands

Run from `web/`.

- Install: `npm install`
- Dev server: `npm start` → http://localhost:4200 (proxies `/api` → backend)
- Build: `npm run build` (output in `dist/web/`)
- Unit tests: `npm test`

> In this workspace the dev server is launched via the preview tooling on
> **port 4203** (see `/Users/radu/Projects/event/.claude/launch.json`,
> entry `yammer-web`). `npm start` defaults to 4200 for everyone else.

## Layout

```
web/
├── proxy.conf.json                    # dev: /api -> http://localhost:8080
├── public/assets/images/logo-abbr.png # the only vendored asset (placeholder logo)
└── src/
    ├── index.html                     # bare — no external stylesheets
    ├── styles.scss                    # global reset + theme tokens (CSS variables)
    ├── environments/                  # environment.ts / environment.prod.ts (apiUrl)
    └── app/
        ├── app.{ts,html,config,routes} # root component + router + providers
        ├── core/
        │   └── auth.service.ts        # login(), token signal, localStorage persistence
        └── features/
            └── auth/login/            # login.{ts,html,scss} — the login screen
```

## Styling & theme

There is no CSS framework. The look is reproduced by hand:

- `src/styles.scss` — global reset and the theme tokens as CSS custom properties:
  `--primary: #3454d1` (Duralux brand indigo), `--text`, `--muted`, `--border`,
  `--danger`, `--page-bg`. Reference these in component styles, don't hard-code hex.
- Each component owns its layout in its `.scss` (scoped). See
  `features/auth/login/login.scss` for the card/form pattern to copy.

The Duralux static template (at `/Users/radu/Projects/yammer/duralux`) is a
**visual reference only** — open the relevant `*.html` to see the intended look,
then rebuild it with semantic markup + scoped SCSS. Do **not** vendor its
Bootstrap/theme CSS or icon fonts back in (that's ~500 kB for one page). The login
screen is modelled on `auth-login-minimal.html`.

## API integration

- `environment.apiUrl` is `/api`; the dev server proxies that to the backend at
  `http://localhost:8080` (Spring Boot app in `../api`). See `proxy.conf.json`.
- `AuthService.login()` POSTs `{ username, password }` to `/api/auth/login` and
  expects `{ token }`. **This backend endpoint does not exist yet** — when it is
  added to the API, confirm the request/response shape matches (or update the
  service). On success the token is stored in `localStorage` under `yammer.token`.

## Conventions

- Standalone components only; import dependencies in the component's `imports`.
- Feature code under `src/app/features/<feature>/...`; shared singletons under
  `src/app/core/`.
- Use signals for component state (`loading`, `error`, etc.) and Reactive Forms
  for inputs.
- Write semantic markup with purpose-built class names; style with scoped SCSS
  using the global theme tokens. No utility-class frameworks.
