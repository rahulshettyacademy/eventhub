# EventHub — Booking Management Test Strategy

Generated: 2026-10-24
Input: `docs/test-scenarios.md` (2026-10-24 revision, 86 scenarios, TC-001 to TC-513)
Supersedes: the 2026-03-06 strategy. Its TC numbers no longer match the scenarios file.

---

## 0. Constraints that shape every decision

1. **Everything targets production.** `playwright.config.ts` sets `baseURL` to `https://eventhub.rahulshettyacademy.com`. API and E2E tests therefore change real data in a shared test account. A test must never change state outside the sandbox of the account it runs as.
2. **There's no Unit or Component runner yet.** The root, `backend` and `frontend` packages have no Jest, Vitest or Playwright CT. This strategy uses:
   - **Unit** → Node's built-in `node:test` in `backend/test/`, which needs no new dependencies. **Don't** put unit files under `tests/`: Playwright's default `testMatch` also picks up `*.test.js` and would try to run them.
   - **Component** → Playwright UI tests where **every** network call is mocked with `page.route()`. Auth is mocked too: put a token in `localStorage.eventhub_token` and mock `GET /api/auth/me`, because `useAuth.tsx:29-45` checks the token on mount. These tests never change backend state, so they're safe against production and can run in parallel.
3. **Shared account plus booking-count rules means serial execution.** FIFO tests need *exactly* 9 bookings, while E2E tests clear all bookings. The two must not run at the same time on one account. See §7.
4. **Never trigger the same-event seat burn on a static event.** `bookingService.js:96` (`decrementSeats`) permanently lowers the DB seat count for **every** user. TC-102 must use a user-created event.

---

## 1. Layer distribution

| Layer | Count | Focus | Approx. run time |
|---|---|---|---|
| **Unit** | 11 | Booking-ref generation, price formula, validator chain (`bookingValidator.js`) | < 2s |
| **API** | 37 | Service rules that need the DB (FIFO, seat accounting, cascades), auth and ownership, error contract | 1–3 min (FIFO setup needs about 9 POSTs per test) |
| **Component (mocked UI)** | 28 | Refund widget, loading/empty/error states, dialogs, pagination, error-screen branches, display formatting | 1–2 min |
| **E2E** | 10 | Critical journeys against the real stack, cross-user UI, admin view | 2–4 min |
| **Total** | **86** | | |

**Shape: closer to a diamond than a pyramid** (11 / 37 / 28 / 10). This is a deliberate trade-off, not an oversight:
- The backend booking rules (`createBooking`, FIFO, per-user seats) are mostly *Prisma queries*: `orderBy createdAt`, `groupBy`, `deleteMany`. Unit tests with mocked repositories would only repeat the implementation and couldn't catch a wrong `where` or `orderBy`. API is the lowest layer that actually tests them.
- The frontend has almost no extracted pure functions. Refund logic (`[id]/page.tsx:26-28`) and `fmt_price` are defined inline in page files.
- **To widen the base:** extract `isRefundEligible(qty)`, `formatPrice(n)` and `pluralizeTickets(n)` into `frontend/lib/`, and export `randomRef` and `generateUniqueRef` from `bookingService.js`. That would move TC-403, TC-413, TC-414 and TC-513 down to Unit, giving roughly 15 / 37 / 24 / 10.

---

## 2. Layer assignments

Each TC has one **primary** layer. Secondary layers for defense-in-depth are listed in §5.

### 2.1 Unit — 11
_Runner: `node --test backend/test/`. The validator is tested by running the `validateCreateBooking` chain on a fake `req` and reading `validationResult(req)`._

| TC | Title | Function under test | Source |
|---|---|---|---|
| TC-106 | Total = price × quantity | `createBooking` with `eventRepository`, `bookingRepository` and `prisma` stubbed through the require cache | `bookingService.js:99` |
| TC-107 | Ref prefix = uppercased first letter of the title | `randomRef(title)` (**needs export**) | `bookingService.js:11-18` |
| TC-409 | Ref prefix for digit, accented and emoji titles | `randomRef('2026 Expo')`, `randomRef('über')`, `randomRef('🎉 Launch')` | `bookingService.js:12` (`title[0]` is a UTF-16 code unit) |
| TC-411 | Ref uniqueness / collision retry | `generateUniqueRef` with `findByRef` stubbed to collide 10 times → expect the `Date.now()` base-36 fallback | `bookingService.js:21-32` |
| TC-306 | Quantity 0, -1, 11, 1.5, "abc", null rejected | `body('quantity').isInt({min:1,max:10})` | `bookingValidator.js:38-41` |
| TC-307 | Name `"A"`, `"   "`, `"  B  "` rejected | `trim().notEmpty().isLength({min:2})` | `bookingValidator.js:21-24` |
| TC-308 | Malformed emails rejected | `isEmail()` | `bookingValidator.js:26-30` |
| TC-309 | Short phone and bad characters rejected | `isLength({min:10})` + `matches(...)` | `bookingValidator.js:32-36` |
| TC-406 | Gmail address normalized | `normalizeEmail()`: assert the sanitized `req.body.customerEmail` | `bookingValidator.js:30` |
| TC-407 | Symbols-only phone **accepted** (validation gap) | Same chain: `"----------"` passes | `bookingValidator.js:35-36` |
| TC-408 | `"1"` / `"3"` coerced to integers | `.toInt()` on `eventId` and `quantity` | `bookingValidator.js:19,41` |

### 2.2 API — 37
_Runner: Playwright `request` fixture against `/api/bookings` (`bookingRoutes.js`). Get the token once per file through `POST /api/auth/login`._

**Happy-path contracts**
| TC | Title | Endpoint → service |
|---|---|---|
| TC-008 | Look up a booking by ref | `GET /ref/:ref` (`bookingRoutes.js:196`) → `getBookingByRef` (`bookingService.js:61`) |
| TC-009 | Create a booking: 201 shape, `message`, ref regex, `event` included | `POST /` (`:318`) → `createBooking` (`:68`) |

**Business rules (need the DB)**
| TC | Title | Code under test |
|---|---|---|
| TC-100 | FIFO prunes the oldest booking on a *different* event | `bookingService.js:72-78`, `findOldestUserBookingExcludingEvent` |
| TC-101 | FIFO skips an older same-event booking | Same, plus fallback ordering |
| TC-102 | Same-event fallback permanently burns seats (**user-created event only**) | `bookingService.js:94-96` → `eventRepository.decrementSeats` |
| TC-108 | Cancelling restores per-user seats | `cancelBooking` (`:126-133`) + `eventService.withPersonalSeats` |
| TC-109 | Per-user seats apply to static events (two accounts) | `eventService.js:6-18` |
| TC-110 | **Defect probe:** a 404 booking still prunes the oldest | Prune at `:72-78` runs before the event check at `:83` |
| TC-111 | **Defect probe:** a 400 (insufficient seats) still prunes | Prune before the seat check at `:86-91` |
| TC-112 | List ordered newest first | `bookingRepository.findAll` `orderBy: { createdAt: 'desc' }` |
| TC-113 | Deleting an event cascades to its bookings | Prisma `onDelete: Cascade` |
| TC-114 | Event FIFO (7th event) cascades to bookings | `eventService` `MAX_USER_DYNAMIC_EVENTS` + cascade |
| TC-115 | Clear all affects only the caller (two accounts) | `clearAllBookings` (`:121-122`) → `deleteMany({ where: { userId } })` |
| TC-116 | `status=cancelled` is always empty | `findAll` `where.status` |

**Security**
| TC | Title | Enforcement point |
|---|---|---|
| TC-201 | Another user's booking by ID → 403 | `bookingService.js:57` |
| TC-202 | Another user's DELETE → **404** (not 403) | `bookingRepository.findById` filters by `userId` (`bookingRepository.js:30`), so `bookingService.js:129` can't be reached |
| TC-203 | Another user's booking by ref → 403 | `bookingService.js:64` |
| TC-204 | All 6 routes return 401 without a token or with the wrong scheme | `authMiddleware.js` (`router.use` at `bookingRoutes.js:8`) |
| TC-205 | Tampered, wrong-secret or expired JWT → 401 | `jwt.verify` in `authMiddleware.js` |
| TC-208 | `userId` in the body is ignored | `createBooking` uses `req.user.userId` |
| TC-209 | `totalPrice`, `status` and `bookingRef` in the body are ignored | Explicit field list in `prisma.booking.create` |
| TC-210 | Can't book another user's private event → 404 | `eventRepository.findById(id, userId)` |
| TC-211 | 403 vs 404 reveals whether a booking exists (document it) | `:56-57`, `:63-64` |

**Negative**
| TC | Title | Code under test |
|---|---|---|
| TC-301 | Unknown ID or ref → 404 with message | `:56`, `:63` |
| TC-302 | Non-numeric ID → expect 400/404, probably 500 | `Number('abc')` → Prisma; no `:id` validator |
| TC-303 | Cancelling twice → 404 | `:128` |
| TC-305 | `{}` body → 400 `Validation failed` with `details[]` for all 5 fields (**error contract**) | `handleValidationErrors` (`bookingValidator.js:3-13`) |
| TC-310 | Insufficient seats → 400 with an exact message | `InsufficientSeatsError` (`:89`) |
| TC-311 | `eventId` 999999 → 404; `0` and `"abc"` → 400 | `:83` + `bookingValidator.js:16-19` |
| TC-314 | `page=-1` → probably 500; `page=0&limit=0` → defaults | `bookingRepository.js:8` (negative `skip`) |

**Edge cases**
| TC | Title | Code under test |
|---|---|---|
| TC-400 | 9th booking doesn't prune | `count >= MAX_USER_BOOKINGS` (`:72`) |
| TC-401 | Quantity 1 and 10 both succeed | Validator bounds plus the service |
| TC-402 | Book exactly the remaining seats, then +1 → 400 "Only 0 seat(s)…" | `:86-91` |
| TC-404 | Clear all with 0 bookings → 200 "0 booking(s) cleared" | `bookingController.clearAllBookings` |
| TC-410 | Lowercase ref lookup (MySQL collation) | `findByRef` |
| TC-412 | **Defect probe:** 3 parallel POSTs at 8 bookings exceed the limit | Count, then insert, with no transaction (`:70-118`) |
| TC-416 | `eventId`, `limit` and `page` filters, plus `totalPages` | `getBookings` (`:37-51`), `findAll` |

### 2.3 Component (mocked UI) — 28
_Runner: Playwright with `page.route('**/api/**')` mocking every call, plus `page.clock` for the 4-second refund timer. No backend state changes._

| TC | Title | Mocked input | Target |
|---|---|---|---|
| TC-007 | Back link and breadcrumb go to `/bookings` | Booking fixture | `[id]/page.tsx` |
| TC-103 | qty 1 → eligible message | `quantity: 1`; `page.clock.fastForward(4000)` | `RefundEligibility` (`[id]/page.tsx:21`) |
| TC-104 | qty 3 → "Group bookings (3 tickets)…" | `quantity: 3` | Same |
| TC-105 | Spinner shows, then the result after 4s | `page.clock.install()`; assert the spinner, then fast-forward | `[id]/page.tsx:26` |
| TC-403 | qty 2 boundary → not eligible | `quantity: 2` | Same |
| TC-207 | Stale token → `/login`, token removed | `GET /auth/me` → 401 | `useAuth.tsx:36-43`, `client.js` interceptor |
| TC-212 | `<img onerror>` in `customerName` shown as text | Fixture with the payload; `page.on('dialog')` must not fire | `[id]/page.tsx`, `admin/bookings/page.tsx` |
| TC-300 | `/bookings/999999` → "Booking not found" | `GET /bookings/999999` → 404 | `[id]/page.tsx:117-128` |
| TC-304 | Cancel when the booking is already gone → error toast, dialog closes | `DELETE` → 404 `{error}` | `[id]/page.tsx` `onError` |
| TC-312 | List error state; Retry recovers | 500, then 200 | `bookings/page.tsx` `isError` |
| TC-313 | **Defect probe:** clear-all 500 → no feedback | `DELETE /bookings` → 500 | `bookings/page.tsx:27-35` (`try/finally`, no `catch`) |
| TC-405 | Dismissing the native confirm → no `DELETE` sent | `page.once('dialog', d => d.dismiss())`; assert no request | `bookings/page.tsx:28` |
| TC-413 | 99.50 × 3 shows as "$100" / "$299" | Fixture with a fractional price | `fmt_price` in `[id]/page.tsx` |
| TC-414 | Price 0 → "$0", still not eligible at qty 2 | Fixture | Same plus `RefundEligibility` |
| TC-415 | `?page=2` with 1 page → empty state, no way back | `pagination.totalPages: 1`, `data: []` | `bookings/page.tsx` + `Pagination.jsx` |
| TC-500 | 5 skeleton cards while loading | Delayed route | `BookingCardSkeleton` |
| TC-501 | Empty state; "Clear all bookings" still visible | `data: []` | `bookings/page.tsx` |
| TC-502 | Detail spinner while loading | Delayed route | `[id]/page.tsx` |
| TC-503 | Dialog copy on the detail page vs the card | Fixture with ref R, qty Q | `[id]/page.tsx`, `BookingCard.jsx` |
| TC-504 | Cancel or overlay close → no `DELETE` | Fixture; assert no request | `ConfirmDialog.jsx` |
| TC-505 | In-flight cancel: Cancel button disabled, can't dismiss | Delayed `DELETE` | `ConfirmDialog.jsx` (`isLoading`) |
| TC-506 | "Clearing…" while pending; no success toast | Delayed `DELETE /bookings` | `bookings/page.tsx` |
| TC-507 | Refund state machine; resets on reload | Fixture plus clock | `RefundEligibility` |
| TC-508 | Breadcrumb and header show the ref and status badge | Fixture | `[id]/page.tsx` |
| TC-509 | 403 → "Access Denied"; 404 and 500 → "Booking not found" | 403 / 404 / 500 | `[id]/page.tsx:119` (`is403`) |
| TC-510 | Pagination shows at `totalPages > 1`; Prev and Next disabled at the ends | `totalPages: 3` | `Pagination.jsx` |
| TC-511 | No sandbox banner on `/bookings` | 9-booking fixture | `bookings/page.tsx` (no banner code) |
| TC-513 | "1 ticket" / "4 tickets", en-IN dates, USD | Fixture | `BookingCard.jsx` |

### 2.4 E2E — 10
_Runner: Playwright against production, logged in for real. Set up data through the API; only drive the UI for the journey under test._

| TC | Title | Why it has to be E2E |
|---|---|---|
| TC-001 | Bookings list shows real cards | Smoke test of the full read path |
| TC-002 | Detail page shows all sections | Smoke test of the full read path for one booking |
| TC-003 | Cancel from the detail page → toast, redirect, gone | Destructive action; real `DELETE` plus navigation |
| TC-004 | Cancel from a card → stays on the page, card removed | Real mutation plus React Query invalidation |
| TC-005 | Clear all → empty state | Destructive bulk action through the native `confirm()` |
| TC-006 | Book → "View My Bookings" → newest first | The one journey that books through the UI |
| TC-010 | Admin bookings: table, modal, cancel | The only coverage of `admin/bookings/page.tsx` |
| TC-200 | User B sees "Access Denied" for User A's booking | Two real sessions; the 403 has to come from the real backend |
| TC-206 | Logged out → `/bookings` and `/bookings/1` redirect to `/login` | Real `AuthGuard` routing |
| TC-512 | List and seats refresh after cancel and after booking | Cache invalidation across pages (`useBookings.ts`). Can share a test with TC-004 / TC-006 |

---

## 3. Decision rationale (contested assignments)

### FIFO and seat rules (TC-100–102, 110, 111, 400) → API, not Unit
The scenarios file suggests API, and this strategy keeps it there, because a unit test would prove little. `createBooking` is a sequence of repository calls (`bookingService.js:70-118`). With mocked repositories, "oldest booking on a different event" becomes whatever the stub returns, so the test only re-checks the `if` statements. The actual rule lives in Prisma queries (`orderBy: { createdAt: 'asc' }`, `eventId: { not }`). Only real DB calls test it. TC-110 and TC-111 (prune before validate) also reproduce easily through the API, with no mocks to keep in sync.

### Validation (TC-306–309, 406–408) → Unit, with **one** API contract test (TC-305)
Rule 5 says push down. Each field rule is a pure express-validator chain (`bookingValidator.js:15-44`). Running the chain on a fake `req` covers every branch in milliseconds. TC-305 stays at API to lock down the HTTP contract: 400, `error: "Validation failed"`, `details: [{ field, message }]`, and the fact that validation runs **before** the service, so no FIFO prune happens. One contract test plus many unit tests is enough. Ten API tests posting bad payloads to production would add nothing.

### Ref prefix (TC-107) → Unit primary, plus API and an existing E2E
It's a pure function, so Unit is primary. It's also a headline business rule. TC-009 (API) checks the regex on real refs, and the existing E2E test confirms the confirmation card shows it. That's three layers on purpose (see §5).

### Refund eligibility (TC-103–105, 403, 507) → Component, no E2E
The logic is 100% client-side (`[id]/page.tsx:21-32`), with no request at all. An E2E test would need login, a real booking and a real 4-second wait. A mocked-UI test with `page.clock.fastForward(4000)` covers every branch, including the qty 2 boundary, in under a second each. The scenarios file suggested "E2E (or Component)", and Component wins.

### Cross-user access → API + Component + E2E (three layers)
- TC-201/202/203 (API) check the server's decision, including the **404-not-403** quirk on DELETE.
- TC-509 (Component) checks every branch of the frontend `is403` condition (403 / 404 / 500) cheaply.
- TC-200 (E2E) is the single real two-session check that the pieces work together. It's a P0 security rule, so it gets three layers.

### TC-109 / TC-115 (two-account isolation) → API, not E2E
Both are server-side `where userId` filters. Two `request` contexts with two tokens prove them with no browser.

### TC-212 (XSS) → Component, not E2E
The risk is in the rendering path (React escaping). Serving the payload from a mock tests that path exactly as well as a stored booking would, and it leaves no hostile data in production.

### TC-300 / TC-509 → Component, not E2E
The old strategy put "Booking not found" at E2E. Its only logic is the `isError` branch, so a mocked 404 is enough. TC-200 already covers the real-backend version of this screen.

### Defect probes (TC-110, 111, 302, 313, 314, 407, 412, 415, 211)
The code reads suggest these currently fail against the documented rules. Write each one to assert the **correct** behavior and mark it `test.fail()` with a comment naming the scenario, so the suite stays green. Once a bug is fixed, Playwright reports the `test.fail()` as an unexpected pass, which tells you to remove the marker. Confirm each failure once by hand before adding `test.fail()`. They were found by reading code, not by running it.

### TC-412 (race) → API, runs alone
It's in its own sandbox, so it's safe on production, but it's timing-dependent. Run it in its own serial `describe` and clean up with `DELETE /api/bookings` in `afterEach`.

---

## 4. Anti-patterns

### 4.1 Avoid when writing the new tests
| Anti-pattern | Affected TCs | Do this instead |
|---|---|---|
| Field validation tested through the UI form | TC-306–309, 407 | Unit on the validator chain; one API contract test (TC-305) |
| API status codes asserted via the UI | TC-201–205, 301, 303 | `request` fixture; the UI only checks the 403 screen (TC-509) |
| A real 4s `setTimeout` wait | TC-103–105, 403, 507 | `page.clock.install()` / `fastForward(4000)` |
| Setting up 9 bookings through the UI | TC-100, 101, 110–112, 400, 412 | 9 × `POST /api/bookings` in `beforeEach` |
| Seat burn on a static event | TC-102 | Create a user-created event in setup and delete it in teardown |
| Hostile payloads saved in production | TC-212 | Serve them from `page.route` |
| Unit files under `tests/` | All Unit | `backend/test/*.test.js`; Playwright would pick up `tests/**/*.test.js` |

### 4.2 Found in the existing `tests/booking-management.spec.js`
| # | Issue | Where | Fix |
|---|---|---|---|
| 1 | **TC IDs no longer match the scenarios file.** The test titled "TC-004" is clear-all (now **TC-005**). "TC-102" is the ref prefix (now **TC-107**). The comment "TC-003 + TC-506" refers to old numbering (TC-506 is now the "Clearing…" label). New TC-004 (cancel from a card) has no test | Test titles and comments | Rename to TC-005 / TC-107, drop the TC-506 reference, add TC-004 |
| 2 | **UI login and UI booking as setup in every test.** `login()` fills the form and `bookEvent()` drives the whole booking form 5 times | `login()`, `bookEvent()` | Log in once through `POST /api/auth/login` and reuse `storageState`, or put the token in localStorage. Create bookings with `request.post('/api/bookings')`. Only TC-006 should book through the UI |
| 3 | **`clearBookings()` is racy.** `isVisible().catch(() => false)` takes one snapshot right after `goto`, before the list loads, so it can see neither state | `clearBookings()` | Replace it with `request.delete('/api/bookings')` |
| 4 | **No teardown.** TC-001, TC-002, TC-006 and TC-102 leave bookings on the shared live account, against CLAUDE.md's cleanup rule | All tests | Add `test.afterEach` that calls `DELETE /api/bookings` |
| 5 | **The breadcrumb assertion checks the wrong element.** `span.font-mono.font-bold` + `.first()` matches the **header badge**. The breadcrumb span is `font-mono` without `font-bold` | TC-002 | Scope to the breadcrumb `nav`, or add `data-testid="breadcrumb-ref"` (a frontend change) |
| 6 | **Event picked unpredictably.** "First card with Book Now" may be a user-created event left by an earlier run, so the title and prefix change between runs | `bookEvent()` | Use a known static event, looked up by title or ID through `GET /api/events` |
| 7 | CSS-class locators where better options exist | `.booking-ref`, `.confirm-booking-btn` | Acceptable for now (documented in `ui-selectors.md`). Prefer `data-testid` if the frontend adds them |
| 8 | Duplicated booking flow | TC-006 repeats `bookEvent()` inline | Fine, since TC-006 is the UI-booking journey. Remove the duplication once item 2 is done |

---

## 5. Defense-in-depth map

| Rule | Unit | API | Component | E2E |
|---|---|---|---|---|
| Ref prefix = first letter of title, uppercased | TC-107, 409 | TC-009 | — | existing spec (→ TC-107) |
| Total = price × qty | TC-106 | TC-009 | TC-413, 414 | TC-001 (card total) |
| Refund: qty 1 eligible, > 1 not | — | — | TC-103, 104, 403 | — |
| Cross-user isolation | — | TC-201, 202, 203, 109, 115 | TC-509 | TC-200 |
| Cancel deletes and restores seats | — | TC-108, 303 | TC-503, 504 | TC-003, 004 |
| Clear all | — | TC-115, 404 | TC-405, 506 | TC-005 |
| FIFO at 9 bookings | — | TC-100, 101, 400, 110, 111, 412 | — | — |
| Auth required | — | TC-204, 205 | TC-207 | TC-206 |
| Quantity 1–10 | TC-306 | TC-305, 401 | — | — |

FIFO is API-only on purpose: it has no UI and the rule lives in the DB queries (§3).

---

## 6. Implementation priority

**Tier 1 — P0 (blocks release)**
- E2E: TC-001, 002, 003, 004, 005, 200, 206
- API: TC-009, 100, 108, 115, 201, 202, 204, 310
- Unit: TC-106, 107, 306
- Component: TC-103, 104

**Tier 2 — P1 (every PR)**
- E2E: TC-006
- API: TC-008, 101, 102, 109, 110†, 111†, 113, 203, 205, 208, 209, 210, 301, 303, 305, 311, 400, 401, 402
- Component: TC-300, 403, 405, 501, 504, 509

**Tier 3 — P2/P3 (nightly or pre-release)**
- E2E: TC-010, 512
- API: TC-112, 114, 116, 211†, 302†, 314†, 404, 410, 412†, 416
- Unit: TC-307, 308, 309, 406, 407†, 408, 409, 411
- Component: TC-007, 105, 207, 212, 304, 312, 313†, 413, 414, 415†, 500, 502, 503, 505, 506, 507, 508, 510, 511, 513

† Defect probe: write it with `test.fail()` until the bug is fixed (§3).

---

## 7. Source file map for `/generate-tests`

| Layer | Test file | Run with | Key sources |
|---|---|---|---|
| Unit | `backend/test/bookingService.test.js`, `backend/test/bookingValidator.test.js` | `node --test backend/test/` (add `"test": "node --test test/"` to `backend/package.json`) | `bookingService.js` (export `randomRef` and `generateUniqueRef`), `bookingValidator.js` |
| API | `tests/booking-api.spec.js` | `npx playwright test tests/booking-api.spec.js` | `bookingRoutes.js`, `bookingService.js`, `bookingRepository.js`, `authMiddleware.js`, `errorHandler.js` |
| Component | `tests/booking-ui-states.spec.js` | Same runner; fully parallel | `bookings/page.tsx`, `bookings/[id]/page.tsx`, `BookingCard.jsx`, `ConfirmDialog.jsx`, `Pagination.jsx`, `useAuth.tsx` |
| E2E | `tests/booking-management.spec.js` (refactor the existing file per §4.2) | Same runner | Full stack |

**Execution and accounts**
- `booking-api.spec.js` and `booking-management.spec.js` both change the bookings of `rahulshetty1@gmail.com`. Run them with `test.describe.configure({ mode: 'serial' })` **and** keep them out of parallel workers (`--workers=1`, or a Playwright project dependency). Otherwise an E2E clear-all will break the API FIFO setup.
- Two-account tests (TC-109, 115, 200, 201–203, 208, 210) use `rahulshetty1@yahoo.com` as User B.
- Optional, needs your approval: register a throwaway account per API spec run through `POST /api/auth/register` for full isolation. This would leave extra accounts on production.
- `booking-ui-states.spec.js` never reaches the backend, so it can run fully parallel at any time.
