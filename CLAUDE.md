# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
EventHub is a full-stack event ticket booking platform built for QA training. Users can browse events, book tickets, manage bookings, and create events. Each user operates in an isolated sandbox.

## Tech Stack
- **Frontend**: Next.js 14 (App Router), React 18, TypeScript + JSX mix, Tailwind CSS, React Query v5, Axios
- **Backend**: Express.js 4, Prisma ORM 5, MySQL 8+, express-validator, Swagger UI (`/api/docs`)
- **Auth**: JWT (7-day expiry), bcryptjs
- **Testing**: Playwright E2E (Chromium only)

## Commands
```bash
npm run setup        # Install backend + frontend deps (root has its own deps: Playwright, concurrently)
npm run dev          # Start backend (3001, nodemon) + frontend (3000) concurrently
npm run db:push      # Push Prisma schema to DB (non-interactive)
npm run migrate      # prisma migrate dev (interactive)
npm run seed         # Seed 10 static events
npm run build        # Next.js production build
npm run lint         # next lint (frontend only; backend has no linter)

npm run test         # Run all Playwright tests
npm run test:ui      # Playwright UI mode
npm run test:report  # Open last HTML report
npx playwright test tests/<file>.spec.js --reporter=line   # Single file
npx playwright test -g "<test title>" --reporter=line      # Single test by name
docker compose up --build                                  # Run tests in Playwright container (image holds only tests/ + config — no app; reports mounted to ./playwright-report)
```

Checks run by CI on PRs (`.github/workflows/ci.yml`) — run these locally before pushing:
```bash
cd frontend && npx tsc --noEmit && npm run build
cd backend && node --check server.js && node --check app.js
cd backend && npx prisma validate && npx prisma format   # CI fails if format changes schema.prisma
```

Env: `backend/.env` needs `DATABASE_URL`, `PORT`, `CORS_ORIGIN`, `JWT_SECRET` (falls back to a hardcoded default in `src/config/env.js`). `frontend/.env.local` needs `NEXT_PUBLIC_API_URL` (default `http://localhost:3001/api`).

## Playwright Target — Important
`playwright.config.ts` sets `baseURL` to the **live deployed site** (`https://eventhub.rahulshettyacademy.com`), not localhost. Tests run against production by default, both locally and in `.github/workflows/playwright.yml` (on push to main). To test local changes, temporarily point `baseURL` at `http://localhost:3000`. The `playwright-best-practices` skill says the base URL is localhost, which is out of date.

## Architecture
Backend is layered: `routes/` (Express routers + Swagger JSDoc) → `validators/` (express-validator) → `controllers/` (thin) → `services/` (business rules) → `repositories/` (Prisma). Domain errors in `src/utils/errors.js` (`NotFoundError`, `ValidationError`, `InsufficientSeatsError`, `ForbiddenError`) carry a `statusCode` and are mapped to JSON responses by `middleware/errorHandler.js`. Responses use the envelope `{ success, data, pagination? }` or `{ success: false, error }`.

**Per-user sandbox** — how isolation actually works:
- `authMiddleware` decodes the JWT into `req.user`; services take `userId` on every call.
- Event queries filter `isStatic: true OR userId = <me>` (`eventRepository`), so users only see seeded events plus their own.
- Seat availability is **computed per user**: `eventService.withPersonalSeats` / `bookingService.createBooking` return `event.availableSeats − (this user's booked quantity)`. Booking and cancelling do not change the DB `availableSeats`. The one exception: when FIFO pruning removes a booking for the same event, `decrementSeats` is called so the displayed count still drops.
- Limits are constants in the services: `MAX_USER_DYNAMIC_EVENTS = 6` and `MAX_USER_BOOKINGS = 9`. When a limit is hit, the oldest item is deleted (FIFO). Booking pruning prefers a booking for a different event.
- Booking refs are `<first letter of event title, uppercase>-<6 alphanumerics>` (the README's `EVT-XXXXXX` is outdated).

Frontend:
- Routes: `/events`, `/events/[id]`, `/bookings`, `/bookings/[id]`, `/login`, `/register`. Event creation/management lives at `/admin/events`, with a bookings admin page at `/admin/bookings`. These are per-user, not a separate admin role.
- Axios client `frontend/lib/api/client.js` attaches `localStorage.eventhub_token`, unwraps response bodies, and redirects to `/login` on 401. Hooks (`lib/hooks/useEvents.ts`, `useBookings.ts`, `useAuth.tsx`) wrap `eventsApi.js` / `bookingsApi.js` / `authApi.js` in React Query.
- `lib/api/client.ts`, `events.ts`, and `bookings.ts` are a parallel fetch-based client that most code doesn't use. Check which client a file imports before you edit it.
- UI elements carry `data-testid` attributes for tests. The full list is in the `eventhub-domain` skill (`ui-selectors.md`) and the README.

Deployment (`deploy.yml`): on merge to main, deploys over SSH and reloads PM2. Prisma migrations are **not** run automatically and must be applied by hand on the server.

## Testing Conventions
- Test files go in `tests/` as `<feature-name>.spec.js`
- Follow the `playwright-best-practices` skill
- Locator priority: data-testid > role > label/placeholder > ID > CSS class
- No `page.waitForTimeout()` — use `expect().toBeVisible()`
- Tests must be self-contained (login → action → assert)
- Use test accounts: `rahulshetty1@gmail.com` / `Magiclife1!`
- `fullyParallel: false`, `retries: 0`. Tests share a single user sandbox, so FIFO pruning from one test can affect another.

## Key Business Rules
- Max 6 user-created events (FIFO pruning on overflow)
- Max 9 bookings per user (FIFO pruning on overflow)
- Booking ref first character = event title first character (uppercase)
- Seat count reduces on booking, restores on cancellation (per-user computed; see above)
- Refund eligibility: 1 ticket = eligible, >1 ticket = not eligible (client-side)
- Cross-user booking access returns "Access Denied" (backend throws `ForbiddenError` → 403)
- Static events (seeded, `isStatic: true`) are immutable

## Skills (`.claude/skills/`)
User-invocable agents:
- `/generate-tests <feature>` — writes and validates Playwright tests
- `/review-tests <file>` — reviews test code quality
- `/create-scenarios <area>` — writes functional scenarios to `docs/test-scenarios.md`
- `/test-strategy <scenarios>` — assigns scenarios to pyramid layers (`docs/test-strategy.md`)

Reference skills (auto-loaded): `eventhub-domain` (business rules, API reference, user flows, UI selectors) and `playwright-best-practices`.

## Code Style
- Backend: JavaScript (CommonJS) with JSDoc, Express patterns
- Frontend: TypeScript for pages/hooks/types; many components are `.jsx`. Match the extension of neighboring files. Tailwind utility classes.
- Tests: JavaScript with Playwright test runner, step comments
