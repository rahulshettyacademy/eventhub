# Test Strategy: Booking Management

Source: `docs/test-scenarios.md` (65 scenarios, TC-001–TC-510). Cross-referenced against
`backend/src/services/bookingService.js`, `backend/src/controllers/bookingController.js`,
`backend/src/validators/bookingValidator.js`, `backend/src/repositories/bookingRepository.js`,
`frontend/app/bookings/page.tsx`, `frontend/app/bookings/[id]/page.tsx`,
`frontend/app/events/[id]/page.tsx`, `.claude/references/playwright-best-practices.md`.

## 0. Tooling reality check (read this before assigning layers)

This repo has **only one test runner installed**: `@playwright/test` (root `package.json`). There
is no Jest, Vitest, React Testing Library, or Mocha anywhere in `backend/` or `frontend/`
(`find ... -iname "*.test.*"` returns nothing). `tests/` is currently empty — no existing spec
files to audit for anti-patterns.

This matters because `docs/test-scenarios.md` assigns many scenarios to "Unit" and "Component"
layers that **cannot be executed today** without either new tooling or a small code refactor.
Rather than propose a new framework, this strategy maps "Unit"/"Component" onto what's already
available:

| Scenario doc's label | What it actually maps to here                                                                                               | Why                                                                                                                                                                                                                                                                                                                                                    |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **API**              | Playwright's built-in `request` fixture (APIRequestContext) hitting Express directly, no browser                            | Already installed, no new dependency, matches `playwright-best-practices.md` §6 route-mocking pattern in spirit                                                                                                                                                                                                                                        |
| **Component**        | A Playwright E2E test scoped to _one page_, with `page.route()` mocking the backend (per `playwright-best-practices.md` §6) | This project has no component-test renderer (no `@playwright/experimental-ct-react`, no RTL). Route-mocked single-page tests are the closest available substitute and require zero new infra                                                                                                                                                           |
| **Unit**             | Node's built-in `node:test` + `node:assert` (zero install) — **but blocked today**                                          | `randomRef` and `generateUniqueRef` in `bookingService.js:11-32` are module-private (only the `bookingService` object is exported at line 139). They cannot be imported or mocked in isolation without a small export change. Frontend's `validate()` in `BookingForm` (`frontend/app/events/[id]/page.tsx:88-95`) is likewise a closure, not exported |

**Recommendation**: if the team wants true Unit coverage for `randomRef`/`generateUniqueRef`,
export them from `bookingService.js` (or move to a `backend/src/utils/bookingRef.js` module) and
add `node --test` as a `pretest` step — this is a ~15-minute change, not a new framework.
Until that happens, TC-111 and TC-406 are **not reliably testable at any layer** (see §4) and TC-101/TC-405
are covered indirectly via API response assertions instead of true unit isolation.

---

## 1. Distribution Table

| Layer                                                         | Count (primary)                                      | Focus                                                                                                                   | Est. suite time                       |
| ------------------------------------------------------------- | ---------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| **Unit** (`node:test`)                                        | 0 shippable today (2 blocked, 2 deferred candidates) | Pure ref-generation logic — blocked on export refactor                                                                  | N/A until refactor (~50ms/test after) |
| **API** (Playwright `request` fixture)                        | 32                                                   | Business rules, validation, auth/ownership, FIFO pruning, pagination contract                                           | ~200–500ms/test, ~10–15s total        |
| **Component** (Playwright + `page.route()` mock, single page) | 22                                                   | Client-side validation, UI states (loading/error/empty), refund-timer logic, stepper boundaries, badge/status rendering | ~1–2s/test, ~30s total                |
| **E2E** (full stack, real DB, multi-page)                     | 6                                                    | Critical booking/cancel/clear-all journeys, cross-user "Access Denied" UI wording, unauth redirect                      | ~3–8s/test, ~40s total                |
| **Blocked / needs clarification**                             | 3 (TC-111, TC-406, TC-510)                           | See §4 and §5                                                                                                           | —                                     |

Shape: wide at API (backend owns almost every business rule — seat math, FIFO, refunds-adjacent
gating, auth), narrower at Component (UI-only concerns), narrowest at E2E (only what genuinely
requires a real multi-page, full-stack path). This inverts the scenario doc's original bias, which
put 25+ scenarios at E2E by default — see §6 for the anti-patterns that caused that.

---

## 2. Layer Assignments

### Unit — 0 active, 2 deferred, 2 blocked

See §4.

### API — 32 scenarios

Endpoint: `POST/GET/DELETE /api/bookings*` via `backend/src/controllers/bookingController.js` →
`backend/src/services/bookingService.js` → `backend/src/repositories/bookingRepository.js`.

| ID                                                     | Endpoint / function under test                                                                           | Why API, not higher                                                                                                                                                                            |
| ------------------------------------------------------ | -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| TC-010                                                 | `GET /api/bookings/ref/:ref`                                                                             | Direct endpoint contract, no UI needed                                                                                                                                                         |
| TC-011                                                 | `bookingRepository.findAll` (limit=10, `skip`/`take`)                                                    | Pagination correctness is a query concern; UI wiring split out to Component (see TC-011 note below)                                                                                            |
| TC-100                                                 | `bookingService.createBooking` → `randomRef` (indirect, via response)                                    | `randomRef` isn't exported (see §0), so response inspection is the only current way to verify `bookingRef[0]`                                                                                  |
| TC-101                                                 | Same as above — regex match on `bookingRef` in POST response                                             | Deferred Unit candidate once exported                                                                                                                                                          |
| TC-103, TC-104                                         | `event.availableSeats` before/after via `GET /api/events/:id`                                            | Core seat math lives entirely server-side; no UI involvement required to prove it                                                                                                              |
| TC-105, TC-106, TC-404                                 | `bookingService.createBooking` lines 68-97 (`findOldestUserBookingExcludingEvent`, `sameEventFallback`)  | **TC-404 is a literal duplicate of TC-106** — same precondition, same assertion. Keep one test (`bookingService.createBooking` same-event fallback), delete the other from the plan            |
| TC-107, TC-108                                         | `bookingRepository.getBookedQuantitiesForEvents` (groupBy quantity sum)                                  | Multi-account isolation logic, no UI needed                                                                                                                                                    |
| TC-109                                                 | `bookingRepository.deleteAllForUser`                                                                     | Bulk-delete contract                                                                                                                                                                           |
| TC-200, TC-201, TC-202, TC-203, TC-204, TC-205         | `getBookingById`/`getBookingByRef`/`cancelBooking` ownership checks (`bookingService.js:54-66, 126-136`) | 403/401/404 status codes and messages are asserted at the API boundary; UI copy is verified separately at Component/E2E (see below)                                                            |
| TC-303, TC-304, TC-305, TC-306, TC-307, TC-311, TC-312 | `bookingValidator.js` (express-validator chain, lines 15-44) + `InsufficientSeatsError`                  | Textbook "input validation belongs at API" — see anti-pattern §6                                                                                                                               |
| TC-308                                                 | `InsufficientSeatsError` bypassing client cap                                                            | Server must reject regardless of UI; UI-disabled-button half is Component (merged with TC-400, see below)                                                                                      |
| TC-309, TC-310                                         | `cancelBooking` / `clearAllBookings` idempotency                                                         | Sequential API calls, no UI needed                                                                                                                                                             |
| TC-400                                                 | Seat boundary (book exact remaining seat)                                                                | Server-side math; UI "SOLD OUT" render is the _same_ Component test as TC-308                                                                                                                  |
| TC-401                                                 | `quantity: 10` accepted (validator `max: 10`)                                                            | Server contract; UI "+" disabled state is the same Component test as TC-509                                                                                                                    |
| TC-403                                                 | FIFO diff-event prune                                                                                    | Distinct from TC-105 in that it's the "10th booking" framing — kept separate since it also exercises `/bookings` list count post-prune, but consider merging with TC-105 if setup is identical |
| TC-405                                                 | `randomRef` fallback char via response                                                                   | Deferred Unit candidate (see §0)                                                                                                                                                               |
| TC-407                                                 | `bookingRepository.findAll` pagination boundary at 10→11                                                 | Backend math; UI page-control appearance split to Component with mocked pagination metadata (avoids creating 11 real DB rows just to check a UI element)                                       |

### Component — 22 scenarios (single-page, `page.route()` mocked, no real backend)

| ID                                                             | Page / component                                                                         | Why this layer                                                                                                                                                                                                                                                                                                                       |
| -------------------------------------------------------------- | ---------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| TC-003                                                         | `bookings/[id]/page.tsx` full render                                                     | All 5 sections are pure rendering off one `GET /bookings/:id` payload — mock it, no need to actually create a booking through the whole flow                                                                                                                                                                                         |
| TC-008, TC-009, TC-110                                         | `RefundEligibility` (`bookings/[id]/page.tsx:21-71`)                                     | **Frontend-only logic, no backend endpoint at all** ("no backend API for refund" — business-rules.md §8). These three IDs are the same boundary test (qty=1 vs qty>1) told three times; write **one** Component test with two mocked bookings (qty=1, qty=2) instead of three                                                        |
| TC-102                                                         | `bookings/[id]/page.tsx` Payment Summary fields                                          | Rendering fidelity of already-computed values — mock a booking with known price/qty/totalPrice                                                                                                                                                                                                                                       |
| TC-204, TC-504                                                 | `bookings/[id]/page.tsx` `is403` branch (line 119)                                       | Same test: mock a non-403 error (e.g. network 404) and assert "Booking not found" vs mock a 403 and assert "Access Denied" copy — both branches, one test file                                                                                                                                                                       |
| TC-300, TC-301, TC-302, TC-410, TC-411                         | `BookingForm.validate` (`events/[id]/page.tsx:88-95`)                                    | Purely client-side, no network call is made until `validate()` passes — asserting "no API call fired" is only checkable if you mock the endpoint and assert it was never hit, which is a Component/route-mock concern, not E2E                                                                                                       |
| TC-308, TC-400 (UI half)                                       | `BookingForm` `soldOut` / disabled button                                                | Mock `event.availableSeats = 0`, assert "Sold Out" text + disabled; same test covers "last seat" transition to 0                                                                                                                                                                                                                     |
| TC-402, TC-401 (UI half), TC-509                               | `BookingForm` stepper (`events/[id]/page.tsx:118-131`)                                   | One parametrized test: `availableSeats=3` → caps at 3; `availableSeats>=10` → caps at 10, "+" disabled                                                                                                                                                                                                                               |
| TC-409                                                         | `RefundEligibility` spinner timing                                                       | Pure `setTimeout(4000)` in a component with no backend call — classic "timing tested at E2E" anti-pattern, push down (see §6)                                                                                                                                                                                                        |
| TC-500, TC-501, TC-502, TC-503, TC-505, TC-506, TC-507, TC-508 | `bookings/page.tsx` and `bookings/[id]/page.tsx` loading/error/empty/badge/button states | All are single-page render states driven by mocked query state (`isLoading`, `isError`, empty array, slow response) — none require real data or multi-page navigation. TC-502 in particular is reassigned down from the scenario doc's "E2E" suggestion since an empty list is just a mocked `{data: [], pagination: null}` response |

Note on TC-505: this is the **only way to test the non-"confirmed" branch at all** — every
booking the app can currently create ends up `status: "confirmed"` (`bookingService.js:113`), so
there is no real code path that produces a cancelled/other-status booking to test against. Mocking
is not just cheaper here, it's the only option.

### E2E — 6 scenarios (full stack, real DB, Chromium)

| ID                              | Journey                                                                                                                        |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| TC-001 (absorbs TC-002, TC-006) | Login → book 1 ticket → confirmation → "View My Bookings" → booking visible in list                                            |
| TC-007                          | Login → increment stepper → book N tickets → confirmation total → detail page total matches                                    |
| TC-004                          | Login → open booking detail → cancel → toast → redirected to list → booking gone                                               |
| TC-408                          | Reuse TC-004 with exactly one booking as precondition → assert empty state renders post-cancel, rather than a separate journey |
| TC-005                          | Login → clear all bookings → confirm dialog → empty state                                                                      |
| TC-200 (UI half)                | User A books → logs out → User B logs in → navigates to A's booking URL → "Access Denied" copy + 403                           |
| TC-203 (UI half)                | Cleared auth state → navigate to `/bookings` → redirected to `/login`                                                          |

Six is deliberately narrow — every one of these requires a real login, a real JWT, and a real
multi-page transition, which is what E2E is for. Everything else in the original 65 either doesn't
need the browser at all (API) or doesn't need the backend at all (Component).

---

## 3. Decision Rationale (contested assignments)

- **TC-007, TC-102, TC-103, TC-104 (seat/price math)**: the scenario doc suggests E2E for all of
  these. The arithmetic (`totalPrice = price × quantity`, seat decrement/restore) is 100% backend
  (`bookingService.js:86-99`), so API is the layer that actually exercises the logic; TC-007 stays
  at E2E only because it's also validating that the stepper UI correctly drives a real multi-qty
  purchase end-to-end — that's a journey concern, not a math concern.
- **TC-008/TC-009/TC-110 (refund eligibility)**: moved from E2E to Component. There is no backend
  endpoint (business-rules.md §8: "frontend-only logic, no backend API"), so requiring a full
  login → book → navigate → click chain to test a `setTimeout` + string comparison is the
  ice-cream-cone anti-pattern in its purest form. A mocked single booking gets the same coverage
  in a fraction of the time.
- **TC-106 vs TC-404**: identical precondition ("9 bookings, 10th for same event"), identical
  assertion (oldest deleted + seat burned). Keeping both in the plan would just double
  maintenance for zero extra confidence — collapse to one.
- **TC-111, TC-406 (ref collision retry)**: scenario doc suggests Unit, but `randomRef`/
  `generateUniqueRef` are unexported and `bookingRepository` isn't injectable, so there is no way
  to force 10 consecutive collisions from outside the module. Testing this via real API calls
  would require spamming bookings until a natural collision occurs (`1/36^6` odds per attempt) —
  not deterministic, not worth attempting. Flagged as blocked (§4), not assigned.
- **TC-204/TC-504 vs TC-200/TC-203**: the _status code_ (403/401/404) is an API concern; the
  _copy on screen_ ("Access Denied" vs "Booking not found" vs redirect-to-login) is a UI concern.
  Split each into its cheapest layer rather than re-deriving the same auth failure through a full
  login+navigate E2E chain every time — TC-204's UI half only needs the error boundary mocked,
  which doesn't require the 403 case at all (a plain 404 exercises the same branch), so it's
  Component, not E2E.
- **TC-511-adjacent stepper tests (TC-401, TC-402, TC-509)**: three IDs describing the same
  `Math.min(10, event.availableSeats)` boundary from slightly different angles. One parametrized
  Component test covers both branches of the `min()`.

---

## 4. Blocked Scenarios (cannot be assigned a layer as-is)

| ID     | Blocker                                                                                                                                                                               | Unblock path                                                                                                                                                                  |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| TC-111 | Booking-ref collision retry (`generateUniqueRef`, `bookingService.js:21-32`) needs `bookingRepository.findByRef` to be mockable to force 10 collisions. Not exported, not injectable. | Export `generateUniqueRef` + accept an injectable repo, or extract to a pure function taking `existingRefs: Set<string>` as a parameter. Then it's a 5-line `node:test` case. |
| TC-406 | Same root cause as TC-111 (fallback-after-10-attempts branch).                                                                                                                        | Same fix as above.                                                                                                                                                            |

Do not leave these silently uncovered in a test plan — call them out explicitly to whoever owns
the backlog so "no test exists for X" is a decision, not an oversight.

---

## 5. Needs Product/Spec Clarification Before Test Authoring

- **TC-510 (sandbox banner on `/bookings`)**: business-rules.md §5 describes "a conditional banner
  ... giving heads-up about booking limits" on the bookings page. Reading the actual component
  (`frontend/app/bookings/page.tsx`, full file, 121 lines) shows no such conditional banner — the
  only static text near the "Clear all bookings" button ("Do this often for clean test data.")
  is unconditional and doesn't reference a count or limit. Either the banner hasn't shipped yet or
  it lives somewhere not yet located. **Do not write TC-510 until someone confirms the banner
  exists** — otherwise the test will be asserting against a feature that isn't there.

---

## 6. Anti-Patterns Found (in the scenario doc's own layer suggestions)

The scenario doc (`docs/test-scenarios.md`) defaults to E2E for ~25 of its 65 scenarios. Several
are textbook anti-patterns per the decision rules in this command:

1. **Input validation tested at E2E, should be API/Component** — TC-300, TC-301, TC-302, TC-410,
   TC-411 (client-side `validate()` boundaries) were all suggested as E2E; none need a backend or
   even a real login, since `BookingForm.validate()` runs before any network call
   (`events/[id]/page.tsx:97-101`, `noValidate` on the `<form>` at line 113 confirms client-only
   gating).
2. **Pure UI timing tested at E2E, should be Component** — TC-409 (4-second refund spinner) has
   zero backend dependency; testing it via full login+booking+navigate is pure overhead.
3. **API error codes tested at E2E, should be API** — TC-200/TC-203's _status code_ portions were
   folded into full-page E2E scenarios in the original doc; splitting status-code assertions
   (API) from copy-on-screen assertions (Component/E2E) cuts redundant setup.
4. **Ice-cream-cone risk from near-duplicate scenarios** — TC-008/TC-009/TC-110,
   TC-106/TC-404, TC-401/TC-402/TC-509, TC-204/TC-504 are four groups of scenarios asserting the
   same underlying branch from slightly different entry points. Left unmerged, this is 12
   redundant tests for what's really 4 pieces of logic.
5. **No E2E for critical flows — checked, not present**: the 6-scenario E2E set above does cover
   every P0 critical journey (book, cancel, clear-all, cross-user denial, unauth redirect), so
   there's no gap on the "always need some E2E" side of the anti-pattern list.

No anti-patterns could be sourced from _existing test code_ — `tests/` is currently empty
(`booking-management.spec.js` was removed; see git status). Once `/generate-tests` produces the
files from this strategy, re-run `/review-tests` against them to catch drift from this plan.

---

## 7. Defense-in-Depth (critical rules covered at multiple layers on purpose)

| Rule                            | API                       | Component                    | E2E                                                                                                           |
| ------------------------------- | ------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------------------------- |
| Cross-user access denied        | ✅ (403/401/404 contract) | ✅ (404 copy via TC-204/504) | ✅ (403 copy via TC-200)                                                                                      |
| Unauthenticated access rejected | ✅ (401)                  | —                            | ✅ (redirect to `/login`)                                                                                     |
| Sold-out event blocked          | ✅ (400 bypass)           | ✅ (disabled button/copy)    | —                                                                                                             |
| FIFO booking pruning            | ✅ (TC-105/106/403)       | —                            | — (covered structurally by API; no UI signal distinguishes pruned vs normal state, so E2E adds no value here) |

This is intentionally asymmetric — FIFO pruning has no user-visible signal beyond "the list has 9
items", so a third E2E layer would just re-prove the API result through a slower path, not add
confidence.
