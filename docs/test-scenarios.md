# EventHub — Booking Management Test Scenarios

Generated: 2026-10-24
Scope: Booking Management — list, detail, cancel, clear all, refund eligibility, booking limits (FIFO), per-user seat accounting, booking reference, admin bookings view, and the booking API (`/api/bookings`).

Sources: `eventhub-domain` skill (`business-rules.md`, `user-flows.md`, `ui-selectors.md`), `backend/src/services/bookingService.js`, `backend/src/repositories/bookingRepository.js`, `backend/src/validators/bookingValidator.js`, `backend/src/middleware/*`, `frontend/app/bookings/**`, `frontend/app/admin/bookings/page.tsx`, `frontend/components/bookings/BookingCard.jsx`, `frontend/lib/hooks/useBookings.ts`.

## Code behaviors that differ from the domain docs

Scenarios below are written against **actual code behavior**. Where code and docs disagree, the scenario says so.

| # | Docs say | Code does | Covered by |
|---|---|---|---|
| D1 | Cross-user access returns 403 | `GET /bookings/:id` and `GET /bookings/ref/:ref` → 403, but `DELETE /bookings/:id` → **404** (the repository filters by `userId`, so the `ForbiddenError` branch can't be reached) | TC-202 |
| D2 | Static events have a fixed `availableSeats` | Static events also get per-user seats (`withPersonalSeats` applies to every event) | TC-109 |
| D3 | Bookings page shows a conditional sandbox banner | `/bookings` has no banner; only `/events` has one | TC-511 |
| D4 | "Check Refund Eligibility" button | Button text is "Check eligibility for refund?" | TC-103 |
| D5 | "Clear all" visible when bookings exist | "Clear all bookings" is **always** rendered, including the empty state | TC-501 |
| D6 | — | The FIFO prune runs **before** the event-exists and seat checks, so a booking that fails still deletes the oldest booking | TC-110, TC-111 |
| D7 | — | List page size is 10 and max bookings is 9, so pagination never shows under normal use | TC-510 |
| D8 | Booking refs have a 6-character suffix | After 10 collisions, the fallback uses a base-36 timestamp suffix of up to 8 characters | TC-411 |

---

## Happy Path

### TC-001: View bookings list with existing bookings
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is logged in and has at least one booking
**Steps**:
1. Click "My Bookings" in the navbar (`data-testid="nav-bookings"`)
2. Wait for the list to load
**Expected Results**: URL is `/bookings`; heading is "My Bookings"; one `booking-card` for each booking. Each card shows the booking ref (`.booking-ref`), a "confirmed" badge, `#<id>` (`data-testid="booking-id"`), event title, event date, ticket count, city, "Booked <date>", the total price, a "View Details" link and a "Cancel Booking" button
**Business Rule**: Flow 4 — Manage Bookings
**Suggested Layer**: E2E

---

### TC-002: View booking detail page
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is logged in and has at least one booking
**Steps**:
1. Go to `/bookings`
2. Click "View Details" on a booking card
**Expected Results**: URL is `/bookings/:id`. Breadcrumb reads "My Bookings / <bookingRef>". The header shows the ref, the status badge and the event title (`h1`). The page has these sections: Event Details (Event, Category, Date, Venue, City), Customer Details (Name, Email, Phone), Payment Summary (Tickets, Price per ticket, Total Paid), Refund, and Booking Information (Booked on, Booking ID `#<id>`)
**Business Rule**: Flow 4; Booking data model
**Suggested Layer**: E2E

---

### TC-003: Cancel a booking from the detail page
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is logged in and has one booking
**Steps**:
1. Open `/bookings/:id`
2. Click "Cancel Booking" in the header
3. Click "Yes, cancel it" (`data-testid="confirm-dialog-yes"`)
**Expected Results**: Toast "Booking cancelled successfully"; redirect to `/bookings`; the booking is gone from the list; `GET /api/bookings/:id` now returns 404
**Business Rule**: Booking cancellation deletes the booking row
**Suggested Layer**: E2E

---

### TC-004: Cancel a booking from the list card
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is logged in and has at least two bookings
**Steps**:
1. Go to `/bookings`
2. Click "Cancel Booking" (`data-testid="cancel-booking-btn"`) on the first card
3. Click "Yes, cancel it"
**Expected Results**: Toast "Booking cancelled successfully"; the user stays on `/bookings`; the dialog closes; that card disappears and the other bookings stay (React Query invalidates `['bookings']`)
**Business Rule**: Flow 4 — cancel individual bookings
**Suggested Layer**: E2E

---

### TC-005: Clear all bookings from the list page
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is logged in and has two or more bookings
**Steps**:
1. Go to `/bookings`
2. Register a dialog handler that accepts the native `confirm()` ("Clear all your bookings? This cannot be undone.")
3. Click "Clear all bookings"
**Expected Results**: The list refreshes to the empty state "No bookings yet"; `GET /api/bookings` returns `data: []` and `pagination.total: 0`
**Business Rule**: "Clear All Bookings" removes all bookings in one action
**Suggested Layer**: E2E

---

### TC-006: "View My Bookings" from the booking confirmation
**Category**: Happy Path
**Priority**: P1
**Preconditions**: User is logged in
**Steps**:
1. Book any event from `/events/:id`
2. On the "Booking Confirmed! 🎉" card, note the ref shown in `.booking-ref`
3. Click "View My Bookings"
**Expected Results**: URL is `/bookings`; the newest booking is the **first** card and its ref matches the confirmation
**Business Rule**: Flow 3 → Flow 4; list is ordered by `createdAt desc`
**Suggested Layer**: E2E

---

### TC-007: Back to My Bookings from the detail page
**Category**: Happy Path
**Priority**: P2
**Preconditions**: User is on `/bookings/:id`
**Steps**:
1. Click "← Back to My Bookings"
2. Open the detail page again and click the "My Bookings" breadcrumb link
**Expected Results**: Both links go to `/bookings`
**Business Rule**: Flow 4 navigation
**Suggested Layer**: E2E

---

### TC-008: Look up a booking by reference via the API
**Category**: Happy Path
**Priority**: P1
**Preconditions**: User owns a booking with a known `bookingRef` and has a valid JWT for `Authorization: Bearer <valid JWT>`
**Steps**:
1. `GET /api/bookings/ref/<bookingRef>` with a Bearer token
**Expected Results**: 200 `{ success: true, data }`; `data.bookingRef` matches; `data.event` is included
**Business Rule**: Booking ref lookup endpoint
**Suggested Layer**: API

---

### TC-009: Create a booking via the API
**Category**: Happy Path
**Priority**: P0
**Preconditions**: Valid JWT; a static event exists with enough seats
**Steps**:
1. `POST /api/bookings` with `{ eventId, customerName: "Test User", customerEmail: "test@example.com", customerPhone: "9876543210", quantity: 2 }`
**Expected Results**: 201 with `message: "Booking confirmed!"`; `data.status === "confirmed"`; `data.totalPrice === event.price × 2`; `data.bookingRef` matches `^[A-Z0-9]-[A-Z0-9]{6}$`; `data.event` is included
**Business Rule**: Booking creation; price calculation
**Suggested Layer**: API

---

### TC-010: View and cancel bookings from Admin → Bookings
**Category**: Happy Path
**Priority**: P2
**Preconditions**: User is logged in and has at least one booking
**Steps**:
1. Go to `/admin/bookings`
2. Check the table and the "<N> total bookings" subtitle
3. Open a booking's detail modal
4. Cancel a booking from the table and confirm
**Expected Results**: The table lists only the current user's bookings (columns Ref, Customer, Event, Qty, Total, Status, Date, Actions); the modal is titled "Booking — <ref>"; after cancelling, toast "Booking cancelled" appears and the row is removed
**Business Rule**: Sandbox isolation applies to the admin view
**Suggested Layer**: E2E

---

## Business Rules

### TC-100: FIFO — the 10th booking prunes the oldest booking on a different event
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User has exactly 9 bookings: the oldest is on Event A, and the others include Event B
**Steps**:
1. Create a 10th booking on Event B
2. `GET /api/bookings`
**Expected Results**: The total is still 9; the oldest booking (Event A) is gone; the new booking is present; no other bookings changed
**Business Rule**: Max 9 bookings per user; FIFO prefers the oldest booking on a *different* event
**Suggested Layer**: API

---

### TC-101: FIFO — the oldest different-event booking is pruned even when the oldest overall is on the same event
**Category**: Business Rule
**Priority**: P1
**Preconditions**: 9 bookings; the oldest overall is on Event B and the second oldest is on Event A
**Steps**:
1. Create a booking on Event B
**Expected Results**: The Event A booking (second oldest) is deleted, not the oldest Event B booking; the total stays 9
**Business Rule**: `findOldestUserBookingExcludingEvent` runs before the plain-oldest fallback
**Suggested Layer**: API

---

### TC-102: FIFO same-event fallback permanently burns seats
**Category**: Business Rule
**Priority**: P1
**Preconditions**: All 9 of the user's bookings are on one user-created event E; note E's `availableSeats` (personal)
**Steps**:
1. Create a 10th booking on E with quantity Q
2. Read E via `GET /api/events/:id`
3. Clear all bookings
4. Read E again
**Expected Results**: Step 1 succeeds and the total stays 9. After step 4, with no bookings left, E's `availableSeats` equals `totalSeats − Q`, not `totalSeats`. The DB value was decremented and is not restored by cancelling
**Business Rule**: Same-event fallback in `bookingService.createBooking` decrements DB seats
**Suggested Layer**: API

---

### TC-103: Refund eligibility — single-ticket booking is eligible
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User has a booking with quantity = 1
**Steps**:
1. Open `/bookings/:id`
2. Click "Check eligibility for refund?" (`#check-refund-btn`)
3. Wait for `#refund-result`
**Expected Results**: The result reads "Eligible for refund. Single-ticket bookings qualify for a full refund." in the green (emerald) style
**Business Rule**: quantity = 1 → eligible (client-side)
**Suggested Layer**: E2E (or Component)

---

### TC-104: Refund eligibility — multi-ticket booking is not eligible
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User has a booking with quantity = 3
**Steps**:
1. Open `/bookings/:id`
2. Click `#check-refund-btn` and wait for `#refund-result`
**Expected Results**: The result reads "Not eligible for refund. Group bookings (3 tickets) are non-refundable." in the red style; the number matches the booking quantity
**Business Rule**: quantity > 1 → not eligible
**Suggested Layer**: E2E (or Component)

---

### TC-105: Refund check shows a spinner for about 4 seconds
**Category**: Business Rule
**Priority**: P2
**Preconditions**: User is on any booking detail page
**Steps**:
1. Click `#check-refund-btn`
2. Check that `#refund-spinner` appears with "Checking your refund eligibility…"
3. Wait for `#refund-result`
**Expected Results**: The spinner shows right away and the check button is hidden. The result appears after about 4s, and the spinner is then removed. In the test, use a web-first assertion with a timeout above 4s; don't use a fixed wait
**Business Rule**: 4-second spinner before the result
**Suggested Layer**: Component (fake timers) / E2E

---

### TC-106: Total price = event price × quantity
**Category**: Business Rule
**Priority**: P0
**Preconditions**: An event with a known price P
**Steps**:
1. Book quantity N
2. Compare the API `totalPrice`, the card total and the detail "Total Paid"
**Expected Results**: `totalPrice === P × N`; the UI shows the same amount formatted as USD with no decimals
**Business Rule**: `totalPrice = event.price × quantity`
**Suggested Layer**: API + E2E

---

### TC-107: Booking ref prefix is the uppercased first letter of the event title
**Category**: Business Rule
**Priority**: P0
**Preconditions**: Events titled "Tech Conference Bangalore" and a user-created "summer fest" (lowercase)
**Steps**:
1. Book each event
2. Inspect the refs on the confirmation, list card and detail page
**Expected Results**: Refs start with "T-" and "S-" respectively, followed by 6 characters from `[A-Z0-9]`
**Business Rule**: Booking ref format `<FIRST_LETTER>-<6 alphanumerics>`
**Suggested Layer**: API + E2E

---

### TC-108: Cancelling restores per-user seats on a user-created event
**Category**: Business Rule
**Priority**: P0
**Preconditions**: A user-created event with `totalSeats` S and no bookings
**Steps**:
1. Book quantity 3 → event detail shows S−3 available
2. Cancel the booking
3. Reload the event detail page
**Expected Results**: Available seats go back to S. The DB `availableSeats` was never changed
**Business Rule**: Seats are computed per user; cancelling deletes the row, which restores the seats
**Suggested Layer**: E2E / API

---

### TC-109: Per-user seat computation also applies to static events
**Category**: Business Rule
**Priority**: P1
**Preconditions**: Static event with personal `availableSeats` A; User B is a second account
**Steps**:
1. User A books quantity 2 → User A sees A−2
2. User B views the same event
3. User A cancels → User A sees A
**Expected Results**: User B still sees A (one user's bookings don't affect another user's seat count). User A's count is restored after cancelling. This contradicts business-rules.md §6 ("static = fixed DB field")
**Business Rule**: `withPersonalSeats` applies to all events; sandbox isolation
**Suggested Layer**: API

---

### TC-110: Failed booking for a non-existent event still prunes the oldest booking (data loss)
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has exactly 9 bookings
**Steps**:
1. `POST /api/bookings` with `eventId: 999999` (valid integer, no such event) and otherwise valid data
2. `GET /api/bookings`
**Expected Results**: Step 1 returns 404 "Event with id 999999 not found". **Expected by the business rule**: still 9 bookings, nothing pruned. **Actual code**: 8 bookings, because the prune runs before the event check. Record this as a defect if it reproduces
**Business Rule**: FIFO pruning should only make room for a booking that actually succeeds
**Suggested Layer**: API

---

### TC-111: Booking rejected for insufficient seats still prunes the oldest booking (data loss)
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has 9 bookings; a user-created event with 2 total seats
**Steps**:
1. `POST /api/bookings` for that event with quantity 5
2. `GET /api/bookings`
**Expected Results**: 400 "Only 2 seat(s) available, but 5 requested". The booking count should stay 9; the code drops it to 8 (same root cause as TC-110)
**Business Rule**: FIFO pruning; seat availability check
**Suggested Layer**: API

---

### TC-112: Booking list is ordered newest first
**Category**: Business Rule
**Priority**: P2
**Preconditions**: User creates bookings X, then Y, then Z
**Steps**:
1. `GET /api/bookings` and load `/bookings`
**Expected Results**: The order is Z, Y, X in both the API response and the UI
**Business Rule**: Repository `orderBy: { createdAt: 'desc' }`
**Suggested Layer**: API

---

### TC-113: Deleting an event cascades to its bookings
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User owns event E and has a booking on it
**Steps**:
1. Delete E from `/admin/events`
2. Go to `/bookings` and open the old booking URL
**Expected Results**: The booking is no longer listed; `/bookings/:id` shows "Booking not found"
**Business Rule**: Booking → Event cascade delete
**Suggested Layer**: API / E2E

---

### TC-114: Event FIFO pruning (7th event) silently removes bookings on the pruned event
**Category**: Business Rule
**Priority**: P2
**Preconditions**: User has 6 user-created events and a booking on the oldest one
**Steps**:
1. Create a 7th event
2. `GET /api/bookings`
**Expected Results**: The oldest event is deleted and its booking disappears through the cascade. Other bookings are unaffected
**Business Rule**: Max 6 user events (FIFO) + cascade delete
**Suggested Layer**: API

---

### TC-115: Clear all deletes only the current user's bookings
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User A and User B each have bookings
**Steps**:
1. User A calls `DELETE /api/bookings`
2. User B calls `GET /api/bookings`
**Expected Results**: User A gets 200 "<n> booking(s) cleared" with the correct n; User B's bookings are unchanged
**Business Rule**: Sandbox isolation; `deleteMany({ where: { userId } })`
**Suggested Layer**: API

---

### TC-116: Booking status is always "confirmed"
**Category**: Business Rule
**Priority**: P3
**Preconditions**: User has bookings
**Steps**:
1. `GET /api/bookings?status=cancelled`
2. On `/admin/bookings`, choose the "Cancelled" status filter
**Expected Results**: Both return an empty result, because cancelling deletes the row rather than setting a status. The admin view shows "No bookings found"
**Business Rule**: Status is always "confirmed"; cancel = delete
**Suggested Layer**: API

---

## Security

### TC-200: Another user's booking shows "Access Denied" (UI)
**Category**: Security
**Priority**: P0
**Preconditions**: User A owns booking #X; User B is a second account
**Steps**:
1. Log in as User B (clear `localStorage` first)
2. Go to `/bookings/X`
**Expected Results**: Title "Access Denied", description "You are not authorized to view this booking.", a "View My Bookings" button, and none of User A's booking data on the page
**Business Rule**: Cross-user access → 403 → "Access Denied"
**Suggested Layer**: E2E

---

### TC-201: Another user's booking by ID returns 403 (API)
**Category**: Security
**Priority**: P0
**Preconditions**: User A owns booking #X; User B has a token
**Steps**:
1. `GET /api/bookings/X` with User B's token
**Expected Results**: 403 `{ success: false, error: "You are not authorized to view this booking" }`; the body contains no booking fields
**Business Rule**: Sandbox isolation
**Suggested Layer**: API

---

### TC-202: Another user can't cancel a booking (API returns 404, not 403)
**Category**: Security
**Priority**: P0
**Preconditions**: User A owns booking #X; User B has a token
**Steps**:
1. `DELETE /api/bookings/X` with User B's token
2. `GET /api/bookings/X` with User A's token
**Expected Results**: Step 1 returns **404** "Booking with id X not found", because `findById` filters by `userId` (docs suggest 403; see D1). Step 2 returns 200, so the booking is untouched
**Business Rule**: Sandbox isolation
**Suggested Layer**: API

---

### TC-203: Another user's booking by reference returns 403
**Category**: Security
**Priority**: P1
**Preconditions**: User A owns a booking with ref R
**Steps**:
1. `GET /api/bookings/ref/R` with User B's token
**Expected Results**: 403 "You do not own this booking"; no booking data in the body
**Business Rule**: Sandbox isolation
**Suggested Layer**: API

---

### TC-204: Booking endpoints reject a missing or malformed token
**Category**: Security
**Priority**: P0
**Preconditions**: None; repeat each request with and without `Authorization: Bearer <valid JWT>`
**Steps**:
1. Call `GET /api/bookings`, `GET /api/bookings/1`, `GET /api/bookings/ref/T-AAAAAA`, `POST /api/bookings`, `DELETE /api/bookings/1` and `DELETE /api/bookings` with (a) no `Authorization` header and (b) `Authorization: Token abc` (wrong scheme)
**Expected Results**: Every call returns 401 `{ success: false, error: "Unauthorized" }`, and no data changes
**Business Rule**: All `/api/bookings` routes require a Bearer JWT
**Suggested Layer**: API

---

### TC-205: Invalid, tampered or expired JWT is rejected
**Category**: Security
**Priority**: P1
**Preconditions**: A valid token T
**Steps**:
1. `GET /api/bookings` with T after changing one character of its signature
2. Repeat with a token signed with a different secret, and with an expired token
**Expected Results**: 401 "Invalid or expired token" in every case
**Business Rule**: JWT verification in `authMiddleware`
**Suggested Layer**: API

---

### TC-206: Unauthenticated user visiting booking pages is redirected to login
**Category**: Security
**Priority**: P0
**Preconditions**: Not logged in (no `eventhub_token` in localStorage)
**Steps**:
1. Go to `/bookings`
2. Go to `/bookings/1`
**Expected Results**: Both redirect to `/login`; no booking data is rendered
**Business Rule**: `AuthGuard` protects pages
**Suggested Layer**: E2E

---

### TC-207: A stale token on the bookings page logs the user out
**Category**: Security
**Priority**: P2
**Preconditions**: User is logged in
**Steps**:
1. Set `localStorage.eventhub_token` to an invalid value
2. Reload `/bookings`
**Expected Results**: The API returns 401; the axios interceptor removes `eventhub_token` and redirects to `/login`
**Business Rule**: 401 handling in `lib/api/client.js`
**Suggested Layer**: E2E

---

### TC-208: `userId` in the request body is ignored when creating a booking
**Category**: Security
**Priority**: P1
**Preconditions**: User A and User B tokens; User B's id is known
**Steps**:
1. As User A, `POST /api/bookings` with valid data plus `userId: <User B id>`
2. Check bookings as both users
**Expected Results**: The booking belongs to User A (it appears in User A's list, not User B's)
**Business Rule**: Ownership comes from the JWT (`req.user.userId`)
**Suggested Layer**: API

---

### TC-209: Client-supplied `totalPrice`, `status` and `bookingRef` are ignored
**Category**: Security
**Priority**: P1
**Preconditions**: Valid token; an event with price > 0
**Steps**:
1. `POST /api/bookings` with valid data plus `totalPrice: 0`, `status: "cancelled"`, `bookingRef: "Z-HACKED"`
**Expected Results**: 201; `totalPrice === price × quantity`; `status === "confirmed"`; `bookingRef` is server-generated and starts with the event's first letter
**Business Rule**: The service builds the booking from an explicit field list (no mass assignment)
**Suggested Layer**: API

---

### TC-210: Can't book another user's private event
**Category**: Security
**Priority**: P1
**Preconditions**: User A owns user-created event E
**Steps**:
1. As User B, `POST /api/bookings` with `eventId: E`
**Expected Results**: 404 "Event with id E not found"; no booking is created
**Business Rule**: Event visibility = static OR owned by the current user
**Suggested Layer**: API

---

### TC-211: The 403 vs 404 difference reveals whether a booking exists
**Category**: Security
**Priority**: P3
**Preconditions**: User B's token
**Steps**:
1. `GET /api/bookings/<id owned by A>` → note the status
2. `GET /api/bookings/<id that doesn't exist>` → note the status
3. Repeat with `/ref/<ref>`
**Expected Results**: Document the current behavior (403 vs 404), which lets anyone check whether a booking ID or ref exists. Raise it as a security finding; the fix would return 404 in both cases
**Business Rule**: Sandbox isolation (information disclosure)
**Suggested Layer**: API

---

### TC-212: Customer fields with HTML or script are rendered as text
**Category**: Security
**Priority**: P2
**Preconditions**: Valid token
**Steps**:
1. Create a booking via the API with `customerName: "<img src=x onerror=alert(1)>"`
2. Open `/bookings/:id` and `/admin/bookings` (open the modal)
**Expected Results**: The name appears as literal text; no script runs and no dialog opens
**Business Rule**: Output encoding (React escaping)
**Suggested Layer**: E2E

---

## Negative / Error

### TC-300: Non-existent booking ID shows "Booking not found"
**Category**: Negative
**Priority**: P1
**Preconditions**: User is logged in
**Steps**:
1. Go to `/bookings/999999`
**Expected Results**: Title "Booking not found", description "This booking doesn't exist or may have been cancelled.", and a "View My Bookings" button
**Business Rule**: 404 handling on the detail page
**Suggested Layer**: E2E

---

### TC-301: Non-existent booking ID and ref return 404 (API)
**Category**: Negative
**Priority**: P1
**Preconditions**: Valid token
**Steps**:
1. `GET /api/bookings/999999`
2. `GET /api/bookings/ref/Z-NOPE00`
**Expected Results**: 404 with `error` "Booking with id 999999 not found" and `Booking with reference "Z-NOPE00" not found`
**Business Rule**: `NotFoundError` → 404
**Suggested Layer**: API

---

### TC-302: Non-numeric booking ID
**Category**: Negative
**Priority**: P2
**Preconditions**: Valid token
**Steps**:
1. `GET /api/bookings/abc` and `DELETE /api/bookings/abc`
2. In the UI, go to `/bookings/abc`
**Expected Results**: The API should return 400 or 404, not 500. **Note**: `Number("abc")` is NaN and goes straight into Prisma, so a 500 "Internal server error" is likely; record it as a defect if it happens. The UI shows "Booking not found"
**Business Rule**: Input validation on path params (no validator on `:id`)
**Suggested Layer**: API

---

### TC-303: Cancelling an already-cancelled booking returns 404
**Category**: Negative
**Priority**: P1
**Preconditions**: User has booking #X
**Steps**:
1. `DELETE /api/bookings/X` → 200 "Booking cancelled"
2. `DELETE /api/bookings/X` again
**Expected Results**: Step 2 returns 404 "Booking with id X not found"
**Business Rule**: Cancel = delete
**Suggested Layer**: API

---

### TC-304: Cancelling a booking that was deleted in another tab
**Category**: Negative
**Priority**: P2
**Preconditions**: User has booking #X open in two tabs
**Steps**:
1. Tab 1: cancel booking X
2. Tab 2 (stale page): click "Cancel Booking" → "Yes, cancel it"
**Expected Results**: Tab 2 shows an error toast with the server message ("Booking with id X not found") and the dialog closes. No crash and no redirect
**Business Rule**: `useCancelBooking` onError → toast
**Suggested Layer**: E2E

---

### TC-305: Missing required fields on create
**Category**: Negative
**Priority**: P1
**Preconditions**: Valid token
**Steps**:
1. `POST /api/bookings` with `{}`
**Expected Results**: 400 `{ success: false, error: "Validation failed", details: [...] }`. `details` includes entries for `eventId`, `customerName`, `customerEmail`, `customerPhone` and `quantity`, each with a "... is required" message. No booking is created and no FIFO prune happens (validation runs before the service)
**Business Rule**: `validateCreateBooking`
**Suggested Layer**: API

---

### TC-306: Invalid quantity values
**Category**: Negative
**Priority**: P0
**Preconditions**: Valid token; valid event
**Steps**:
1. `POST /api/bookings` with `quantity` set to each of: `0`, `-1`, `11`, `1.5`, `"abc"`, `null`
**Expected Results**: Each returns 400 with a `details` entry `{ field: "quantity", message: "Quantity must be an integer between 1 and 10" }` (or "Quantity is required" for null)
**Business Rule**: Quantity 1–10
**Suggested Layer**: API (Unit for the validator)

---

### TC-307: Invalid customer name
**Category**: Negative
**Priority**: P2
**Preconditions**: Valid token
**Steps**:
1. Create with `customerName: "A"`, then `"   "`, then `"  B  "`
**Expected Results**: `"A"` and `"  B  "` (trimmed to 1 character) → "Customer name must be at least 2 characters"; `"   "` → "Customer name is required"
**Business Rule**: Name min 2 characters after trimming
**Suggested Layer**: API

---

### TC-308: Invalid customer email
**Category**: Negative
**Priority**: P2
**Preconditions**: Valid token
**Steps**:
1. Create with `customerEmail` set to `"plainaddress"`, `"a@"`, `"@b.com"`
**Expected Results**: 400 "Customer email must be a valid email address"
**Business Rule**: Valid email format
**Suggested Layer**: API

---

### TC-309: Invalid customer phone
**Category**: Negative
**Priority**: P2
**Preconditions**: Valid token
**Steps**:
1. Create with `customerPhone` set to `"123456789"` (9 digits), `"98765abcde"` and `"98765@4321"`
**Expected Results**: The 9-digit value → "Customer phone must be at least 10 digits"; values with letters or symbols → "Customer phone must contain only digits and +, -, spaces, or parentheses"
**Business Rule**: Phone min 10, restricted character set
**Suggested Layer**: API

---

### TC-310: Insufficient seats
**Category**: Negative
**Priority**: P0
**Preconditions**: User-created event with 3 seats; user has fewer than 9 bookings
**Steps**:
1. Book quantity 2 → success
2. Book quantity 2 again
**Expected Results**: Step 2 returns 400 "Only 1 seat(s) available, but 2 requested"; no booking is created
**Business Rule**: Per-user seat availability check
**Suggested Layer**: API

---

### TC-311: Booking a non-existent event
**Category**: Negative
**Priority**: P1
**Preconditions**: Valid token; fewer than 9 bookings
**Steps**:
1. `POST /api/bookings` with `eventId: 999999`, then with `eventId: 0` and `eventId: "abc"`
**Expected Results**: 999999 → 404 "Event with id 999999 not found"; `0` and `"abc"` → 400 "Event ID must be a positive integer"
**Business Rule**: Event must exist and be visible
**Suggested Layer**: API

---

### TC-312: Bookings list error state
**Category**: Negative
**Priority**: P2
**Preconditions**: User is logged in
**Steps**:
1. Mock `GET **/api/bookings*` to return 500 (or abort it)
2. Go to `/bookings`
3. Remove the mock and click "Retry"
**Expected Results**: Shows "Couldn't load bookings" / "Failed to connect to the server. Please try again." with a Retry button; Retry loads the list
**Business Rule**: Error state on the list page
**Suggested Layer**: E2E (mocked)

---

### TC-313: Clear-all failure gives no feedback
**Category**: Negative
**Priority**: P3
**Preconditions**: User has bookings
**Steps**:
1. Mock `DELETE **/api/bookings` to return 500
2. Accept the confirm and click "Clear all bookings"
**Expected Results**: The button goes back to "Clear all bookings" and the bookings stay. **Note**: `handleClearAll` has `try/finally` with no `catch`, so no error toast appears and the rejection is unhandled. Record it as a UX defect
**Business Rule**: Clear all (error handling)
**Suggested Layer**: E2E (mocked)

---

### TC-314: Negative page number on the list API
**Category**: Negative
**Priority**: P3
**Preconditions**: Valid token
**Steps**:
1. `GET /api/bookings?page=-1`
2. `GET /api/bookings?page=0&limit=0`
**Expected Results**: Step 2 falls back to page 1, limit 10 (because `Number(0) || default`). Step 1 should return 400 or page 1; a negative `skip` probably makes Prisma throw a 500, so record it as a defect if it does
**Business Rule**: Pagination parameter handling
**Suggested Layer**: API

---

## Edge Cases

### TC-400: 9th booking — at the limit, nothing is pruned
**Category**: Edge Case
**Priority**: P1
**Preconditions**: User has exactly 8 bookings
**Steps**:
1. Create one more booking
**Expected Results**: Total is 9; all 8 earlier bookings are still there
**Business Rule**: Prune only when the count is already ≥ 9
**Suggested Layer**: API

---

### TC-401: Quantity at the boundaries (1 and 10)
**Category**: Edge Case
**Priority**: P1
**Preconditions**: An event with ≥ 10 personal seats available
**Steps**:
1. Book quantity 1, then quantity 10
**Expected Results**: Both succeed; cards show "1 ticket" (singular) and "10 tickets"; totals are price × 1 and price × 10
**Business Rule**: Quantity 1–10
**Suggested Layer**: API + E2E

---

### TC-402: Booking exactly the remaining seats, then one more
**Category**: Edge Case
**Priority**: P1
**Preconditions**: User-created event with 5 seats
**Steps**:
1. Book quantity 5
2. Check the event's available seats
3. Book quantity 1
**Expected Results**: Step 1 succeeds; step 2 shows 0 available; step 3 returns 400 "Only 0 seat(s) available, but 1 requested"
**Business Rule**: Seat availability boundary
**Suggested Layer**: API

---

### TC-403: Refund boundary at quantity = 2
**Category**: Edge Case
**Priority**: P1
**Preconditions**: Booking with quantity 2
**Steps**:
1. Run the refund eligibility check
**Expected Results**: "Not eligible for refund. Group bookings (2 tickets) are non-refundable."
**Business Rule**: Only quantity = 1 is eligible
**Suggested Layer**: Component

---

### TC-404: Clear all with zero bookings
**Category**: Edge Case
**Priority**: P2
**Preconditions**: User has no bookings
**Steps**:
1. Click "Clear all bookings" (it's visible in the empty state) and accept the confirm
2. Call `DELETE /api/bookings` directly
**Expected Results**: No error; the API returns 200 "0 booking(s) cleared"; the empty state stays
**Business Rule**: Clear all is idempotent
**Suggested Layer**: API

---

### TC-405: Dismissing the clear-all confirm keeps all bookings
**Category**: Edge Case
**Priority**: P1
**Preconditions**: User has bookings
**Steps**:
1. Register a dialog handler that **dismisses** the native confirm
2. Click "Clear all bookings"
**Expected Results**: No `DELETE /api/bookings` request is sent; all cards stay
**Business Rule**: Clear all requires confirmation
**Suggested Layer**: E2E

---

### TC-406: Gmail addresses are normalized when stored
**Category**: Edge Case
**Priority**: P2
**Preconditions**: Valid token
**Steps**:
1. Create a booking with `customerEmail: "John.Doe+qa@GMAIL.com"`
2. Read the booking back and view the detail page
**Expected Results**: The stored and displayed email is normalized (lowercased, with dots and the `+tag` removed for Gmail, e.g. `johndoe@gmail.com`) by `normalizeEmail()`. Confirm this is the intended behavior, since the customer sees a different email than they typed
**Business Rule**: Validator `normalizeEmail()` (discovered behavior)
**Suggested Layer**: API

---

### TC-407: Phone made only of symbols passes validation
**Category**: Edge Case
**Priority**: P2
**Preconditions**: Valid token
**Steps**:
1. Create a booking with `customerPhone: "----------"` (10 hyphens) and with `"(+) (+) (+)"`
**Expected Results**: Per the rule "min 10 digits", these should be rejected. **Actual**: the length check counts characters, not digits, and the regex allows `+-() `, so they're accepted. Record as a validation defect
**Business Rule**: Customer phone min 10 digits
**Suggested Layer**: API (Unit for the validator)

---

### TC-408: Numeric strings for quantity and eventId are coerced
**Category**: Edge Case
**Priority**: P3
**Preconditions**: Valid token
**Steps**:
1. `POST /api/bookings` with `eventId: "1"`, `quantity: "3"`
**Expected Results**: 201; the stored `quantity` is the integer 3; `totalPrice` = price × 3
**Business Rule**: `.toInt()` sanitization
**Suggested Layer**: API

---

### TC-409: Ref prefix for unusual event title first characters
**Category**: Edge Case
**Priority**: P3
**Preconditions**: User-created events titled "2026 Expo", "über Party" and "🎉 Launch"
**Steps**:
1. Book each event
**Expected Results**: "2-XXXXXX", "Ü-XXXXXX"; the emoji title takes only the first UTF-16 code unit (`title[0]`), so the ref may contain a broken surrogate character. Check how it renders and whether `/ref/<ref>` lookup still works
**Business Rule**: Ref prefix = `title[0].toUpperCase()`
**Suggested Layer**: API

---

### TC-410: Ref lookup with a lowercase reference
**Category**: Edge Case
**Priority**: P3
**Preconditions**: User owns a booking with ref `T-A3B2C1`
**Steps**:
1. `GET /api/bookings/ref/t-a3b2c1`
**Expected Results**: Document the behavior. With MySQL's default case-insensitive collation this probably returns the booking (200); decide whether that's intended
**Business Rule**: Booking ref uniqueness and lookup
**Suggested Layer**: API

---

### TC-411: Booking refs are unique across many bookings
**Category**: Edge Case
**Priority**: P3
**Preconditions**: Valid token
**Steps**:
1. Create and collect 50+ booking refs, allowing FIFO pruning to keep the account under the limit
2. In a unit test, mock `findByRef` to force all 10 collision attempts
**Expected Results**: Ordinary refs match `^[A-Z0-9]-[A-Z0-9]{6}$` and are unique. The forced-collision case remains unique, but the current timestamp fallback can produce a suffix longer than six characters; record any format mismatch as a defect
**Business Rule**: Ref guaranteed unique by collision retry
**Suggested Layer**: Unit

---

### TC-412: Concurrent bookings at 8 can exceed the 9-booking limit
**Category**: Edge Case
**Priority**: P2
**Preconditions**: User has exactly 8 bookings
**Steps**:
1. Send 3 `POST /api/bookings` requests in parallel (`Promise.all`)
2. `GET /api/bookings`
**Expected Results**: The rule says the total should be 9. The count-then-insert is not atomic, so 10–11 bookings may exist; record as a race-condition defect if so. Also check for a 404 "Record not found" (P2025) if two requests at count 9 both try to prune the same oldest booking
**Business Rule**: Max 9 bookings per user
**Suggested Layer**: API

---

### TC-413: Fractional prices and the rounded display
**Category**: Edge Case
**Priority**: P3
**Preconditions**: User-created event with price 99.50
**Steps**:
1. Book quantity 3
2. Compare the API `totalPrice` with the UI's "Price per ticket" and "Total Paid"
**Expected Results**: The API total is 298.5. The UI rounds to whole dollars (`maximumFractionDigits: 0`), showing about "$100" per ticket and "$299" total, so per ticket × quantity doesn't match the displayed total. Document it or file it as a display defect
**Business Rule**: Price calculation and display
**Suggested Layer**: Component

---

### TC-414: Free event (price 0)
**Category**: Edge Case
**Priority**: P3
**Preconditions**: User-created event with price 0
**Steps**:
1. Book quantity 2 and open the detail page
**Expected Results**: `totalPrice` 0, shown as "$0"; the refund check still says "Not eligible" (it's based only on quantity)
**Business Rule**: price ≥ 0; refund logic is quantity-only
**Suggested Layer**: API + Component

---

### TC-415: `/bookings?page=2` when everything fits on page 1
**Category**: Edge Case
**Priority**: P2
**Preconditions**: User has 1–9 bookings
**Steps**:
1. Go to `/bookings?page=2`
**Expected Results**: The page shows the "No bookings yet" empty state even though bookings exist, and there's no pagination to get back (totalPages = 1 hides it). Record as a UX defect; the expected behavior is to clamp to the last page or show a page-specific empty message
**Business Rule**: List pagination
**Suggested Layer**: E2E

---

### TC-416: List API filters (`eventId`, `status`, `limit`)
**Category**: Edge Case
**Priority**: P3
**Preconditions**: User has bookings on Event A and Event B
**Steps**:
1. `GET /api/bookings?eventId=<A>`
2. `GET /api/bookings?status=confirmed`
3. `GET /api/bookings?status=cancelled`
4. `GET /api/bookings?limit=2&page=2`
**Expected Results**: Step 1 returns only Event A's bookings with a matching `pagination.total`. Step 2 returns only confirmed bookings. Step 3 returns an empty result because cancelling deletes bookings rather than changing their status. Step 4 returns the 3rd and 4th newest bookings, with `totalPages = ceil(total / 2)`
**Business Rule**: Repository filters and pagination
**Suggested Layer**: API

---

## UI State

### TC-500: Skeletons while the list loads
**Category**: UI State
**Priority**: P3
**Preconditions**: User is logged in
**Steps**:
1. Delay `GET **/api/bookings*` with a route handler
2. Go to `/bookings`
**Expected Results**: 5 pulsing skeleton cards show until the response arrives, then the real cards replace them
**Business Rule**: Loading state
**Suggested Layer**: Component / E2E (mocked)

---

### TC-501: Empty state with no bookings
**Category**: UI State
**Priority**: P1
**Preconditions**: User has no bookings (clear all first)
**Steps**:
1. Go to `/bookings`
2. Click "Browse Events"
**Expected Results**: Shows "No bookings yet" with the description "You haven't booked any events yet. Browse upcoming events and grab your tickets!" and a "Browse Events" button that goes to `/events`. "Clear all bookings" is **still visible** (see D5)
**Business Rule**: Empty state
**Suggested Layer**: E2E

---

### TC-502: Spinner while the detail page loads
**Category**: UI State
**Priority**: P3
**Preconditions**: User owns a booking
**Steps**:
1. Delay `GET **/api/bookings/<id>`
2. Go to `/bookings/<id>`
**Expected Results**: A centered large spinner shows, then the detail content
**Business Rule**: Loading state
**Suggested Layer**: Component / E2E (mocked)

---

### TC-503: Cancel dialog content (detail page vs card)
**Category**: UI State
**Priority**: P2
**Preconditions**: Booking with ref R and quantity Q
**Steps**:
1. On the detail page, click "Cancel Booking"
2. On the list card, click "Cancel Booking"
**Expected Results**: Both dialogs are titled "Cancel this booking?" and have "Cancel" and "Yes, cancel it" buttons. Detail description: "Cancelling R will release Q seat(s) back to the event. This cannot be undone." Card description: "This will cancel R and release Q seat(s) back to the event. This action cannot be undone."
**Business Rule**: Confirmation before cancelling
**Suggested Layer**: Component

---

### TC-504: Closing the cancel dialog without confirming
**Category**: UI State
**Priority**: P1
**Preconditions**: User has a booking
**Steps**:
1. Open the cancel dialog and click "Cancel"
2. Open it again and close it with the modal's close control or the overlay
**Expected Results**: The dialog closes; no `DELETE` request is sent; the booking stays
**Business Rule**: Confirmation required
**Suggested Layer**: E2E

---

### TC-505: Cancel dialog while the request is in flight
**Category**: UI State
**Priority**: P3
**Preconditions**: Slow `DELETE **/api/bookings/*` (mocked delay)
**Steps**:
1. Confirm a cancellation
2. While it's pending, try to close the dialog
**Expected Results**: "Yes, cancel it" shows its loading state; "Cancel" is disabled and the modal can't be dismissed until the request finishes
**Business Rule**: `ConfirmDialog` `isLoading` handling
**Suggested Layer**: Component

---

### TC-506: "Clearing…" label while clear-all is in flight
**Category**: UI State
**Priority**: P3
**Preconditions**: User has bookings; slow `DELETE **/api/bookings`
**Steps**:
1. Accept the confirm and click "Clear all bookings"
**Expected Results**: The button reads "Clearing…" and is disabled until the request finishes, then reads "Clear all bookings" again. No success toast is shown (none is implemented)
**Business Rule**: Clear-all loading state
**Suggested Layer**: E2E (mocked)

---

### TC-507: Refund widget states
**Category**: UI State
**Priority**: P2
**Preconditions**: User is on a booking detail page
**Steps**:
1. Check the initial state
2. Click the check button
3. Wait for the result
4. Reload the page
**Expected Results**: idle (only `#check-refund-btn`) → checking (only `#refund-spinner`) → result (only `#refund-result`; the button doesn't come back). After a reload it's idle again, since the result isn't saved
**Business Rule**: Client-side refund state machine
**Suggested Layer**: Component

---

### TC-508: Breadcrumb and header show the booking ref
**Category**: UI State
**Priority**: P2
**Preconditions**: Booking with ref R
**Steps**:
1. Open `/bookings/:id`
**Expected Results**: The breadcrumb's last item is R (monospace); the header badge shows R; the status badge reads "confirmed" in the success style
**Business Rule**: Detail page layout
**Suggested Layer**: E2E

---

### TC-509: Access Denied vs Not Found screens
**Category**: UI State
**Priority**: P1
**Preconditions**: None
**Steps**:
1. Mock `GET **/api/bookings/1` to return 403, load `/bookings/1`
2. Mock it to return 404, then 500, and reload each time
**Expected Results**: 403 → "Access Denied" / "You are not authorized to view this booking."; 404 and 500 → "Booking not found" / "This booking doesn't exist or may have been cancelled." All three show a "View My Bookings" button
**Business Rule**: `error.status === 403` decides which screen shows
**Suggested Layer**: Component / E2E (mocked)

---

### TC-510: Pagination appears only when there's more than one page
**Category**: UI State
**Priority**: P3
**Preconditions**: Mock `GET **/api/bookings*` to return 10 items with `pagination: { total: 25, page: 1, limit: 10, totalPages: 3 }`
**Steps**:
1. Load `/bookings`
2. Click "Next →", then page "3"
**Expected Results**: The pagination nav shows; "← Prev" is disabled on page 1; the URL updates to `?page=2` and then `?page=3`; "Next →" is disabled on page 3. Without the mock, real data (max 9 bookings, page size 10) never shows pagination (see D7)
**Business Rule**: List pagination
**Suggested Layer**: Component / E2E (mocked)

---

### TC-511: No sandbox banner on the bookings page
**Category**: UI State
**Priority**: P3
**Preconditions**: User has 8–9 bookings
**Steps**:
1. Go to `/bookings`
2. Go to `/events`
**Expected Results**: `/events` shows the sandbox banner ("Your sandbox holds up to 9 bookings and you can create up to 6 custom events…"). `/bookings` has **no** banner, which contradicts business-rules.md §5. Confirm with the product owner whether this is a missing feature or outdated docs
**Business Rule**: Sandbox warning banners
**Suggested Layer**: E2E

---

### TC-512: List refreshes after cancelling and after booking
**Category**: UI State
**Priority**: P2
**Preconditions**: User has bookings
**Steps**:
1. Cancel one booking from a card
2. Book a new event and click "View My Bookings"
**Expected Results**: No manual reload needed: the cancelled card disappears right away and the new booking appears at the top. Event seat counts also refresh, since `['events']` is invalidated
**Business Rule**: React Query cache invalidation in `useCancelBooking` / `useCreateBooking`
**Suggested Layer**: E2E

---

### TC-513: Card pluralization and formatting
**Category**: UI State
**Priority**: P3
**Preconditions**: Bookings with quantity 1 and quantity 4
**Steps**:
1. View `/bookings`
**Expected Results**: The cards show "🎫 1 ticket" and "🎫 4 tickets". Dates use the en-IN short format (e.g. "24 Oct 2026"). Totals are USD with no decimals
**Business Rule**: Card display rules
**Suggested Layer**: Component
