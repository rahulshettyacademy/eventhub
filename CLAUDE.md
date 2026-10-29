# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
EventHub is a full-stack event ticket booking platform built for QA training. Users browse events, book tickets, manage bookings, and create their own events. Every user operates in an isolated sandbox (see "Per-user sandbox" below).

- **Frontend**: Next.js 14 (App Router), React 18, TypeScript + JSX, Tailwind, React Query v5 — port 3000
- **Backend**: Express 4, Prisma 5, MySQL 8 — port 3001, Swagger UI at `/api/docs`
- **Auth**: JWT (7-day expiry, bcryptjs); all `/api/events` and `/api/bookings` routes require `Authorization: Bearer <token>`
- **Tests**: Playwright E2E (`tests/`, Chromium) and Selenium WebDriver E2E (`selenium/`, Core Java + TestNG, Chrome)

## Commands
All from the repo root unless noted.

```bash
npm run setup        # npm install in backend/ and frontend/ (root has its own deps: Playwright, concurrently)
npm run db:push      # push Prisma schema to DB (non-interactive)
npm run migrate      # prisma migrate dev (interactive)
npm run seed         # seed static events
npm run dev          # backend (nodemon) + frontend (next dev) concurrently
npm run build        # next build
npm run lint         # next lint (frontend only; backend has no linter)

npm test                                                   # all Playwright tests
npx playwright test tests/<file>.spec.js --reporter=line  # single file
npx playwright test -g "<test title>"                      # single test by name
npm run test:ui / npm run test:report
./run-tests.sh [all|playwright|selenium]                   # both suites in Docker, in parallel (run-tests.cmd on Windows)
docker compose run --rm --build playwright                 # one suite; or `selenium`
```

Docker setup: `docker/playwright.Dockerfile` and `docker/selenium.Dockerfile` (each with its own `.dockerignore`), wired up in `docker-compose.yml`. Reports go to `./reports/` (gitignored). `docker compose up --build` runs both suites in parallel.

What CI checks on PRs (`.github/workflows/ci.yml`) — run these locally before pushing schema/frontend changes:
- `cd backend && node --check server.js && node --check app.js`
- `cd backend && npx prisma validate && npx prisma format` — CI **fails if `prisma format` changes `schema.prisma`**
- `cd frontend && npx tsc --noEmit && npm run build`
- A schema-drift job diffs the PR's `schema.prisma` against the production DB. Migrations are **never** run by the deploy pipeline; they're applied manually on the server with `prisma migrate deploy`.

Env: `backend/.env` (`DATABASE_URL`, `PORT`, `CORS_ORIGIN`, `JWT_SECRET`, `SHOW_EXPLORE_LINKS`) and `frontend/.env.local` (`NEXT_PUBLIC_API_URL`, including the `/api` suffix). See the `.example` files.

## Important: tests target the live site
`playwright.config.ts` sets `baseURL` to `https://eventhub.rahulshettyacademy.com`, and specs also hardcode that `BASE_URL`. Tests do **not** run against the local dev server unless you change both. The `.github/workflows/e2e-tests.yml` workflow runs all Playwright specs and then all Selenium Java tests against production, and uploads both HTML reports as artifacts. Its push-to-`main` trigger, like `deploy.yml`'s, is currently commented out (paused), so both run only when started manually from the Actions tab.

## Backend architecture
Layered: `routes → controllers → services → repositories → Prisma` (`backend/src/`).
- **Routes** attach `authMiddleware` (sets `req.user` from the JWT) plus `express-validator` validators, and carry the Swagger JSDoc that `config/swagger.js` turns into the API docs. When you change an endpoint, update its JSDoc.
- **Services** hold business rules and throw domain errors from `utils/errors.js` (`NotFoundError`, `ForbiddenError`, `InsufficientSeatsError`, `ValidationError`). `middleware/errorHandler.js` maps those, plus Prisma codes P2002/P2025/P2003, to HTTP status codes. Responses look like `{ success, data, pagination? }` or `{ success: false, error }`.
- **Repositories** are pure Prisma access. Most take `userId` and apply ownership filters themselves.

### Per-user sandbox (spans repository + service layers)
- Event visibility: `isStatic: true` (seeded, shared, immutable) **OR** `userId === currentUser`. `eventRepository.findById/findAll` enforce this, so another user's event just returns 404.
- **Seat counts are computed per user, not stored.** `availableSeats` in the DB is normally not decremented on booking. `eventService.withPersonalSeats` subtracts the current user's booked quantity on read. Cancelling deletes the booking row, which "restores" the seats. The one exception is the FIFO same-event fallback in `bookingService.createBooking`, which permanently decrements DB seats.
- Limits use FIFO pruning, not rejection: max 6 user-created events (`MAX_USER_DYNAMIC_EVENTS`) and max 9 bookings per user (`MAX_USER_BOOKINGS`; prunes the oldest booking on a *different* event first).
- Booking ref format: `<first letter of event title, uppercased>-<6 alphanumerics>` (e.g. `T-A3B2C1`). README's `EVT-` format is outdated.
- Viewing another user's booking → 403, shown as "Access Denied" in the UI.
- Refund eligibility (1 ticket = eligible, >1 = not) is computed **client-side** only.

## Frontend architecture
- `lib/providers.jsx` wraps the app in React Query + Toast. `lib/hooks/useAuth.tsx` stores the JWT in `localStorage` under `eventhub_token`. `components/auth/AuthGuard.tsx` protects pages.
- **There are two parallel API layers in `lib/api/`.** The axios one (`client.js`, `authApi.js`, `eventsApi.js`, `bookingsApi.js`) attaches the JWT, unwraps `response.data`, and redirects to `/login` on 401. It's used by the hooks. The fetch/TS one (`client.ts`, `events.ts`, `bookings.ts`, `index.ts`) has no auth handling and includes calls that don't match the backend (e.g. `PATCH /bookings/:id/cancel`). Since `./client` exists as both `.js` and `.ts`, check which one resolves before editing. Prefer the axios layer for authenticated calls.
- Path alias `@/*` → `frontend/*`. The codebase mixes `.jsx` and `.tsx`; match the file you're in.

## Testing conventions
- Specs go in `tests/<feature-name>.spec.js` (JavaScript).
- `tests/library-book.spec.js` is the exception: it tests the live Library API at `http://216.10.245.166`, not EventHub, through the `libraryApi` fixture in `tests/fixtures/library-api.js` (which deletes any book a test leaves behind). The same test exists in Rest Assured (`LibraryBookTest`) and Cypress.
- Locator priority: `data-testid` > role > label/placeholder > id > CSS class. No `page.waitForTimeout()`; use web-first `expect(...)` assertions.
- Tests must be self-contained (login → action → assert) and clean up their own data (e.g. clear bookings), since all runs share the live account.
- Test accounts: Playwright uses `rahulshetty1@gmail.com` / `Magiclife1!` (plus `rahulshetty1@yahoo.com` as the second user). Selenium uses its own pair (`selenium/.../config/Config.java`, overridable with `SELENIUM_*` env vars), which `BaseTest` registers when missing. Data is per user, so the two suites can run in parallel. Within a suite, tests still run serially (`workers: 1` / `parallel="none"`), and two runs of the same suite must not overlap.
- Full standards: `.claude/skills/playwright-best-practices/SKILL.md`. Domain reference (API, business rules, user flows, UI selectors): `.claude/skills/eventhub-domain/`.
- Workspace agents: `.github/agents/generate-tests.agent.md` creates and validates Playwright tests; `.github/agents/review-tests.agent.md` reviews Playwright tests; `.github/agents/generate-tests-selenium-java.agent.md` creates and validates Selenium Java tests; `.github/agents/review-tests-selenium-java.agent.md` reviews Selenium Java tests; `.github/agents/playwright-best-practices.agent.md` provides the Playwright testing standards; `.github/agents/selenium-best-practices.agent.md` provides Selenium testing standards; `.github/agents/test-strategy.agent.md` maps scenarios to test pyramid layers.
- Selenium tests are Core Java (TestNG + Maven) in a separate `selenium/` module, never under `tests/`. Standards: `.github/agents/selenium-best-practices.agent.md` (source: `.claude/skills/selenium-best-practices/SKILL.md`). Run with `mvn -f selenium/pom.xml test`.
- `restassured/` is a standalone Rest Assured + TestNG module that checks a sample courses API response (`src/test/resources/data/courses-sample.json`) against a JSON Schema (`schemas/courses-schema.json`). It isn't an EventHub API, and no live endpoint serves this JSON (`rahulshettyacademy.com/getCourse.php` returns 500; other paths return the site's HTML). Each test class serves the sample, or a changed copy of it, from a local JDK `HttpServer` on a random port, so nothing touches a live site. The schema must stay **draft-04**, because Rest Assured's `json-schema-validator` doesn't support newer drafts (use `definitions`, not `$defs`). Run with `mvn -f restassured/pom.xml test`.
  `LibraryBookTest` in the same module is the exception: it calls the live Library API at `http://216.10.245.166` (add a book, get it by ID, delete it). That API's `GetBook.php?ID=` returns an **array** of books, not a single object.
- `cypress/` is a standalone Cypress module (own `package.json`) with the same Library API test as `LibraryBookTest`, written with `cy.request` (`e2e/library-book.cy.js`). The book data is in `fixtures/book.json`, the API calls are custom commands (`cy.addBook`, `cy.getBookById`, `cy.deleteBook`) in `support/commands.js`, and the returned ID is passed between steps as the `@bookId` alias. Run from the repo root with `npm install --prefix cypress` then `npm test --prefix cypress`.
- `python/` is a standalone pytest + `requests` module with the same Library API test (`tests/test_library_book.py`; API helpers in `library_api.py`, fixtures in `conftest.py`). Its virtualenv is `python/.venv` (gitignored; create it with `python -m venv .venv` and `.venv\Scripts\python.exe -m pip install -r requirements.txt`). Run from inside `python/` with `.venv\Scripts\python.exe -m pytest`, because `pytest.ini` there sets `testpaths = tests` and `pythonpath = .` (so tests can `import library_api`). From the repo root, use `python\.venv\Scripts\python.exe -m pytest -c python\pytest.ini --rootdir python`.
- `python/` is a standalone pytest + `requests` module with the same Library API test (`tests/test_library_book.py`). `library_api.py` holds the `Book` dataclass payload and `LibraryApiClient`; the `library_api` fixture in `conftest.py` deletes any leftover books after each test. Set up with `python -m venv python/.venv` and `pip install -r python/requirements.txt`, then run `pytest` from `python/`.

## Project skills (`.claude/skills/`)
- `/generate-tests <feature>` — writes Playwright tests and validates them in a real browser
- `/generate-tests-selenium-java <feature>` — writes Selenium WebDriver tests in Core Java and validates them in a real browser
- `/review-tests <file>` — reviews Playwright test quality against the best-practices skill
- `/review-tests-selenium-java <class>` — reviews Selenium Java test quality against `selenium-best-practices`
- `.github/agents/review-tests-selenium-java.agent.md` — workspace agent for reviewing Selenium Java tests
- `/create-scenarios <area>` — writes functional scenarios to `docs/test-scenarios.md`
- `/test-strategy <scenarios>` — assigns scenarios to pyramid layers (`docs/test-strategy.md`)
- `eventhub-domain`, `playwright-best-practices` and `selenium-best-practices` are reference-only skills loaded by the above.

## Code style
- Backend: JavaScript with JSDoc, Express patterns
- Frontend: TypeScript, React hooks, Tailwind utility classes
- Tests: JavaScript with Playwright test runner; add step comments in tests
- Use meaningful variable names and keep functions focused and single-responsibility
