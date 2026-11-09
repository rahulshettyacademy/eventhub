# EventHub — Booking Management Test Scenarios

Generated: 2026-03-06 · Refreshed: 2026-11-09
Scope: Booking Management (Flow 4 — view, cancel, clear all, refund eligibility, `/admin/bookings`, bookings API)

Sources: `eventhub-domain` skill (business-rules, user-flows, ui-selectors), `frontend/app/bookings/**`, `frontend/app/admin/bookings/page.tsx`, `frontend/components/bookings/BookingCard.jsx`, `backend/src/services/bookingService.js`, `backend/src/repositories/bookingRepository.js`, `backend/src/validators/bookingValidator.js`, `backend/src/middleware/*`.

> Scenarios apply six lenses: Happy Path (TC-001–099), Business Rules (TC-100–199), Security (TC-200–299), Negative/Error (TC-300–399), Edge Cases (TC-400–499), and UI State (TC-500–599). Scope is Flow 4 plus booking-related API and `/admin/bookings` behavior.
>
> **Discrepancies found in code (worth checking first):**
> 1. **TC-409**: At 9 bookings, FIFO pruning runs **before** the event-exists and seat checks, so a booking request that fails still deletes the user's oldest booking.
> 2. **TC-113**: Cancel is a hard delete and `status` is always `confirmed`, so the `/admin/bookings` "Cancelled" filter can never return anything.
> 3. **TC-516**: The domain skill describes a booking-limit banner on `/bookings`, but the page has none. Only `/events` has one.

---

## Happy Path

### TC-001: View bookings list with existing bookings
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is logged in; user has at least one booking
**Steps**:
1. Navigate to `/bookings`
2. Observe the booking cards (`getByTestId('booking-card')`)
**Expected Results**: Heading "My Bookings" is shown. Each card shows the booking ref (`.booking-ref`), a "confirmed" status badge, `#<id>` (`getByTestId('booking-id')`), event title, event date, "N ticket(s)", city, "Booked <date>", the total price, and both a "View Details" link and a "Cancel Booking" button
**Business Rule**: Flow 4 — Manage Bookings; `BookingCard.jsx`
**Suggested Layer**: E2E

---

### TC-002: View single booking detail page
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is logged in; user has at least one booking
**Steps**:
1. Navigate to `/bookings`
2. Click "View Details" on any booking card
3. Observe the booking detail page at `/bookings/:id`
**Expected Results**: Page shows event details (title, category, date, venue, city), customer details (name, email, phone), payment summary (tickets, price per ticket, total paid), the booking ref in the breadcrumb and header, the "Booked on" date, "Booking ID #<id>", and a "Check eligibility for refund?" button
**Business Rule**: Booking model fields; Flow 4
**Suggested Layer**: E2E

---

### TC-003: Cancel a single booking from the detail page
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is logged in; user has at least one booking
**Steps**:
1. Navigate to `/bookings/:id`
2. Click "Cancel Booking"
3. Click "Yes, cancel it" (`getByTestId('confirm-dialog-yes')`)
**Expected Results**: Toast "Booking cancelled successfully" appears; user is redirected to `/bookings`; the cancelled booking is no longer listed
**Business Rule**: `DELETE /api/bookings/:id` hard-deletes the booking
**Suggested Layer**: E2E

---

### TC-004: Clear all bookings from the bookings list page
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is logged in; user has at least one booking
**Steps**:
1. Navigate to `/bookings`
2. Click "Clear all bookings"
3. Accept the browser confirm "Clear all your bookings? This cannot be undone."
**Expected Results**: All bookings are removed; the page shows the "No bookings yet" empty state with a "Browse Events" button
**Business Rule**: `DELETE /api/bookings` → `clearAllBookings`; the authenticated API client attaches the user's JWT
**Suggested Layer**: E2E

---

### TC-005: Navigate back to bookings list from detail page
**Category**: Happy Path
**Priority**: P2
**Preconditions**: User is on a booking detail page
**Steps**:
1. Click "← Back to My Bookings" (bottom of the page)
2. Return to the detail page and click the "My Bookings" breadcrumb link
**Expected Results**: Both actions navigate to `/bookings`
**Business Rule**: UI navigation
**Suggested Layer**: E2E

---

### TC-006: Navigate to bookings via "View My Bookings" after completing a booking
**Category**: Happy Path
**Priority**: P1
**Preconditions**: User just completed a booking (confirmation card shown)
**Steps**:
1. Click "View My Bookings" on the confirmation card
**Expected Results**: User lands on `/bookings`. The new booking is the **first** card, and its ref matches the `.booking-ref` on the confirmation card
**Business Rule**: Flow 3 → Flow 4; list ordered `createdAt desc`
**Suggested Layer**: E2E

---

### TC-007: Lookup booking by reference via API
**Category**: Happy Path
**Priority**: P1
**Preconditions**: User is authenticated; user owns a booking with a known `bookingRef`
**Steps**:
1. `GET /api/bookings/ref/:ref` with a valid JWT
**Expected Results**: 200 `{ success: true, data }`; `data` includes the nested `event`
**Business Rule**: `bookingService.getBookingByRef`
**Suggested Layer**: API

---

### TC-008 🆕: Cancel a booking directly from the list card
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User has ≥ 2 bookings
**Steps**:
1. Navigate to `/bookings`
2. Click "Cancel Booking" (`getByTestId('cancel-booking-btn')`) on one card
3. Click "Yes, cancel it"
**Expected Results**: Toast "Booking cancelled successfully" appears; the user **stays** on `/bookings`; that card disappears and the other cards remain
**Business Rule**: `BookingCard.handleCancel` → `useCancelBooking` invalidates the `['bookings']` query
**Suggested Layer**: E2E

---

### TC-009 🆕: Admin bookings page lists the user's bookings
**Category**: Happy Path
**Priority**: P1
**Preconditions**: User has N bookings (1 ≤ N ≤ 9)
**Steps**:
1. Navigate to `/admin/bookings`
**Expected Results**: Heading "Manage Bookings" and the text "N total bookings" are shown. The table has columns Ref, Customer, Event, Qty, Total, Status, Date, Actions, with one row per booking, newest first
**Business Rule**: `/admin/bookings` is per-user (not a separate admin role); `useBookings({ limit: 15 })`
**Suggested Layer**: E2E

---

### TC-010 🆕: Admin bookings — "View" opens a detail modal
**Category**: Happy Path
**Priority**: P2
**Preconditions**: User has ≥ 1 booking
**Steps**:
1. On `/admin/bookings`, click "View" on a row
2. Close the modal
**Expected Results**: Modal titled "Booking — <ref>" shows ref, status, event title/date/city, customer name/email/phone, tickets, total, and booked-on date. Closing the modal leaves the table unchanged
**Business Rule**: `BookingModal` in `admin/bookings/page.tsx`
**Suggested Layer**: E2E / Component

---

### TC-011 🆕: Admin bookings — cancel a booking from the table
**Category**: Happy Path
**Priority**: P1
**Preconditions**: User has ≥ 1 booking
**Steps**:
1. On `/admin/bookings`, click "Cancel" on a row
2. Confirm "Yes, cancel it"
**Expected Results**: Toast "Booking cancelled" (shorter wording than on `/bookings`); the row disappears; the "N total bookings" count drops by 1
**Business Rule**: `confirmCancel` in `AdminBookingsPage`
**Suggested Layer**: E2E

---

### TC-012 🆕: List API supports `eventId` and `status` filters
**Category**: Happy Path
**Priority**: P2
**Preconditions**: User has bookings for events A and B
**Steps**:
1. `GET /api/bookings?eventId=<A>`
2. `GET /api/bookings?status=confirmed`
**Expected Results**: (1) returns only bookings for event A, with `pagination.total` matching. (2) returns all bookings
**Business Rule**: `bookingRepository.findAll` `where` filters
**Suggested Layer**: API

---

## Business Rules

### TC-100: FIFO pruning — 10th booking replaces oldest booking from a different event
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User has exactly 9 bookings across different events
**Steps**:
1. Note the oldest booking ID
2. `POST /api/bookings` for a new event
3. `GET /api/bookings`
**Expected Results**: `pagination.total` is still 9; the oldest booking is gone; the new booking is present
**Business Rule**: `MAX_USER_BOOKINGS = 9`; pruning prefers a booking for a different event
**Suggested Layer**: API

---

### TC-101: FIFO pruning — same-event fallback permanently burns a seat
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has exactly 9 bookings, all for the SAME event X
**Steps**:
1. Note event X's `availableSeats` as shown to this user
2. Create a 10th booking for event X with quantity q
3. Re-fetch event X
**Expected Results**: The oldest booking is deleted and the new one created. The displayed seat count drops by q, not by 0, because `decrementSeats` permanently reduces the DB `availableSeats` by q
**Business Rule**: `sameEventFallback` path in `bookingService.createBooking`
**Suggested Layer**: API

---

### TC-102: Booking reference first character matches event title first character
**Category**: Business Rule
**Priority**: P0
**Preconditions**: Event with a known title (e.g., "Tech Conference Bangalore")
**Steps**:
1. Book the event
2. Read `bookingRef` from the confirmation card / API response
**Expected Results**: `bookingRef` matches `/^T-[A-Z0-9]{6}$/`
**Business Rule**: Rule 7; `randomRef` prefix = `eventTitle[0].toUpperCase()`
**Suggested Layer**: E2E / API / Unit

---

### TC-103: Refund eligibility — single ticket booking is eligible
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User has a booking with quantity = 1
**Steps**:
1. Open `/bookings/:id`
2. Click "Check eligibility for refund?" (`#check-refund-btn`)
3. Wait for `#refund-result`
**Expected Results**: Green result: "Eligible for refund. Single-ticket bookings qualify for a full refund."
**Business Rule**: Rule 8 — quantity === 1 → eligible
**Suggested Layer**: E2E

---

### TC-104: Refund eligibility — multi-ticket booking is NOT eligible
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User has a booking with quantity = 3
**Steps**:
1. Open `/bookings/:id`, click "Check eligibility for refund?", wait for `#refund-result`
**Expected Results**: Red result: "Not eligible for refund. Group bookings (3 tickets) are non-refundable."
**Business Rule**: Rule 8 — quantity > 1 → not eligible
**Suggested Layer**: E2E

---

### TC-105: Refund eligibility spinner shows for approximately 4 seconds
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User is on a booking detail page
**Steps**:
1. Click "Check eligibility for refund?"
2. Assert `#refund-spinner` ("Checking your refund eligibility…") is visible
3. Assert `#refund-result` appears (use an assertion timeout above 4s, not `waitForTimeout`)
**Expected Results**: The spinner shows immediately, then is replaced by the result after about 4s. No network request is made
**Business Rule**: Rule 8 — `setTimeout(..., 4000)`; client-side only
**Suggested Layer**: E2E / Component (fake timers)

---

### TC-106: Total price is calculated as price × quantity
**Category**: Business Rule
**Priority**: P0
**Preconditions**: Event with a known price
**Steps**:
1. Book "Tech Conference Bangalore" ($1499) × 3
2. View the booking detail
**Expected Results**: "Total Paid" shows $4,497. API `totalPrice` = 4497. "Price per ticket" shows $1,499
**Business Rule**: Rule 9
**Suggested Layer**: E2E / API / Unit

---

### TC-107: Bookings list page size is 10, so a 9-booking cap fits on one page
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has 9 bookings
**Steps**:
1. Open `/bookings` (the page requests `limit=10`)
2. `GET /api/bookings` with no params
**Expected Results**: All 9 cards are on page 1 and no pagination controls render. The API defaults to `pagination.limit = 10`, `totalPages = 1`
**Business Rule**: `MAX_USER_BOOKINGS = 9`; `getBookings` default limit 10. The domain skill says "9 per page", but the code uses 10
**Suggested Layer**: API / E2E

---

### TC-108: Cancelling a booking restores the user's computed seat count
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has a booking with quantity q for an event (static or dynamic)
**Steps**:
1. Note the event's available seats on `/events/:id`
2. Cancel the booking
3. Re-open the event (and also check `GET /api/events/:id`)
**Expected Results**: Available seats increase by exactly q. The DB `availableSeats` is unchanged; only the per-user computation changes
**Business Rule**: Seats = `event.availableSeats − this user's booked quantity` (applies to all events)
**Suggested Layer**: API / E2E

---

### TC-109: "Clear all bookings" control is always visible on `/bookings`
**Category**: Business Rule
**Priority**: P3
**Preconditions**: (a) user has bookings; (b) user has zero bookings
**Steps**:
1. Open `/bookings` in each state
**Expected Results**: The "Clear all bookings" button and the hint "Do this often for clean test data." show in both states. The button is not conditional on bookings existing
**Business Rule**: `bookings/page.tsx` header renders the button unconditionally
**Suggested Layer**: Component

---

### TC-110 🆕: Bookings are listed newest first
**Category**: Business Rule
**Priority**: P2
**Preconditions**: User creates bookings B1, then B2, then B3
**Steps**:
1. Open `/bookings`; read the `.booking-ref` values in order
**Expected Results**: The order is B3, B2, B1 (same order on `/admin/bookings` and in the API)
**Business Rule**: `findAll` `orderBy: { createdAt: 'desc' }`
**Suggested Layer**: API / E2E

---

### TC-111 🆕: Cancelling updates seats on the events pages without a reload
**Category**: Business Rule
**Priority**: P2
**Preconditions**: User has a booking for event X with quantity q; the `['events']` query is cached (event X was visited)
**Steps**:
1. Visit `/events/X` and note the seats
2. Navigate client-side to `/bookings` and cancel the booking from the card
3. Navigate client-side back to `/events/X`
**Expected Results**: Seats show the restored value (+q) without a hard reload
**Business Rule**: `useCancelBooking.onSuccess` invalidates `['events']`
**Suggested Layer**: E2E

---

### TC-112 🆕: Clear all restores seat counts for every booked event
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has bookings for events A (q1) and B (q2)
**Steps**:
1. Note the seats for A and B
2. `DELETE /api/bookings`
3. Re-fetch A and B
**Expected Results**: A's seats go up by q1 and B's by q2. Response: `{ success: true, message: "2 booking(s) cleared" }`
**Business Rule**: Per-user seat computation; `clearAllBookings`. Note: the UI path only calls `refetch()` on the bookings query and does not invalidate `['events']`, so seats on cached event pages can be stale until refetched
**Suggested Layer**: API (+ E2E for the stale-cache check)

---

### TC-113 🆕: Cancellation hard-deletes; "cancelled" status is never produced
**Category**: Business Rule
**Priority**: P2
**Preconditions**: User has bookings; one is cancelled
**Steps**:
1. `GET /api/bookings?status=cancelled`
2. On `/admin/bookings`, choose the "Cancelled" filter
3. `GET /api/bookings/:cancelledId`
**Expected Results**: (1) returns `data: []`, `total: 0`. (2) shows "No bookings found". (3) returns 404. Every booking returned has `status: "confirmed"`
**Business Rule**: `cancelBooking` → `bookingRepository.delete`; `createBooking` always sets `status: 'confirmed'`. The "Cancelled" filter and the red badge variant can never appear (product gap)
**Suggested Layer**: API

---

## Security

### TC-200: Cross-user booking access shows "Access Denied" (UI)
**Category**: Security
**Priority**: P0
**Preconditions**: Users A (`rahulshetty1@gmail.com`) and B (`rahulshetty1@yahoo.com`); A has a booking
**Steps**:
1. As A, create a booking and note its ID
2. Clear `localStorage`, log in as B
3. Open `/bookings/:A_id`
**Expected Results**: "Access Denied" with "You are not authorized to view this booking." and a "View My Bookings" button. None of A's booking data is rendered
**Business Rule**: Rule 2; `getBookingById` → `ForbiddenError` → 403
**Suggested Layer**: E2E

---

### TC-201: Cross-user booking access returns 403 via API
**Category**: Security
**Priority**: P0
**Preconditions**: A has a booking; B has a JWT
**Steps**:
1. `GET /api/bookings/:A_id` with B's JWT
**Expected Results**: 403 `{ success: false, error: "You are not authorized to view this booking" }`
**Business Rule**: `getBookingById` ownership check
**Suggested Layer**: API

---

### TC-202: Cross-user booking cancellation is rejected (404) and does not delete
**Category**: Security
**Priority**: P0
**Preconditions**: A has a booking; B has a JWT
**Steps**:
1. `DELETE /api/bookings/:A_id` with B's JWT
2. `GET /api/bookings/:A_id` with A's JWT
**Expected Results**: (1) returns **404** "Booking with id X not found". It is not 403, because `findById(id, userId)` already filters by owner, so the `ForbiddenError` branch is unreachable. (2) returns 200, so the booking still exists
**Business Rule**: `bookingService.cancelBooking`. The domain rule says cross-user access is 403, so flag the inconsistency with GET (403) for the team
**Suggested Layer**: API

---

### TC-203: Unauthenticated access to bookings list returns 401
**Category**: Security
**Priority**: P0
**Preconditions**: No JWT
**Steps**:
1. `GET /api/bookings` with no `Authorization` header
**Expected Results**: 401 `{ success: false, error: "Unauthorized" }`
**Business Rule**: `router.use(authMiddleware)` on all `/api/bookings` routes
**Suggested Layer**: API

---

### TC-204: Unauthenticated access to booking detail and ref lookup returns 401
**Category**: Security
**Priority**: P0
**Preconditions**: No JWT
**Steps**:
1. `GET /api/bookings/:id`
2. `GET /api/bookings/ref/:ref`
**Expected Results**: Both return 401 "Unauthorized"
**Business Rule**: `authMiddleware`
**Suggested Layer**: API

---

### TC-205: Unauthenticated DELETE (single and all) returns 401
**Category**: Security
**Priority**: P0
**Preconditions**: No JWT; bookings exist for some user
**Steps**:
1. `DELETE /api/bookings`
2. `DELETE /api/bookings/:id`
**Expected Results**: Both return 401 and no bookings are deleted
**Business Rule**: `authMiddleware`
**Suggested Layer**: API

---

### TC-206: Cross-user booking lookup by ref returns 403
**Category**: Security
**Priority**: P1
**Preconditions**: A has a booking with ref R; B has a JWT
**Steps**:
1. `GET /api/bookings/ref/R` with B's JWT
**Expected Results**: 403 "You do not own this booking"
**Business Rule**: `getBookingByRef` ownership check
**Suggested Layer**: API

---

### TC-207 🆕: Bookings list is isolated per user
**Category**: Security
**Priority**: P0
**Preconditions**: A and B each have bookings
**Steps**:
1. As B, open `/bookings` and `/admin/bookings`
2. `GET /api/bookings` (and `?eventId=<event A booked>`) with B's JWT
**Expected Results**: Only B's bookings appear. None of A's refs show, and `pagination.total` counts only B's bookings
**Business Rule**: `findAll` `where: { userId }`
**Suggested Layer**: API / E2E

---

### TC-208 🆕: Clear all only deletes the caller's bookings
**Category**: Security
**Priority**: P0
**Preconditions**: A and B each have ≥ 1 booking
**Steps**:
1. `DELETE /api/bookings` with A's JWT
2. `GET /api/bookings` with B's JWT
**Expected Results**: A's bookings are gone and B's bookings are untouched
**Business Rule**: `deleteAllForUser(userId)`
**Suggested Layer**: API

---

### TC-209 🆕: Invalid, tampered, or expired JWT is rejected
**Category**: Security
**Priority**: P1
**Preconditions**: None
**Steps**:
1. `GET /api/bookings` with `Authorization: Bearer garbage`
2. Send a valid token with a modified payload (e.g. a different `userId`) but the old signature
3. Send a token signed with a different secret
4. Send a valid token without the `Bearer ` prefix
**Expected Results**: Steps 1–3 return 401 "Invalid or expired token". Step 4 returns 401 "Unauthorized"
**Business Rule**: `authMiddleware` `jwt.verify`
**Suggested Layer**: API

---

### TC-210 🆕: Expired/invalid session on `/bookings` redirects to login
**Category**: Security
**Priority**: P1
**Preconditions**: `localStorage.eventhub_token` set to an invalid value
**Steps**:
1. Open `/bookings`, `/bookings/:id`, and `/admin/bookings`
**Expected Results**: Redirect to `/login`; `eventhub_token` is removed from `localStorage`
**Business Rule**: Axios response interceptor on 401
**Suggested Layer**: E2E

---

### TC-211 🆕: Cannot book another user's dynamic event
**Category**: Security
**Priority**: P1
**Preconditions**: A owns dynamic event E; B has a JWT
**Steps**:
1. `POST /api/bookings` with `eventId: E` and B's JWT
**Expected Results**: 404 "Event with id E not found"; no booking is created for B
**Business Rule**: `eventRepository.findById(id, userId)` filters on `isStatic OR userId = me`
**Suggested Layer**: API

---

## Negative / Error

### TC-300: Navigate to non-existent booking ID shows "Booking not found"
**Category**: Negative
**Priority**: P1
**Preconditions**: User logged in
**Steps**:
1. Open `/bookings/99999999`
**Expected Results**: "Booking not found", "This booking doesn't exist or may have been cancelled.", and a "View My Bookings" button
**Business Rule**: 404 → non-403 error branch
**Suggested Layer**: E2E

---

### TC-301: GET /api/bookings/:id with non-existent ID returns 404
**Category**: Negative
**Priority**: P1
**Preconditions**: Authenticated
**Steps**:
1. `GET /api/bookings/99999999`
**Expected Results**: 404 "Booking with id 99999999 not found"
**Business Rule**: `getBookingById` NotFoundError
**Suggested Layer**: API

---

### TC-302: Create booking with insufficient seats returns 400
**Category**: Negative
**Priority**: P0
**Preconditions**: The user's available seats for event X = n (e.g. a small dynamic event)
**Steps**:
1. `POST /api/bookings` with `quantity: n + 1`
**Expected Results**: 400 "Only n seat(s) available, but n+1 requested"; no booking is created
**Business Rule**: `InsufficientSeatsError` when `personalAvailable < quantity`
**Suggested Layer**: API

---

### TC-303: Create booking for non-existent event returns 404
**Category**: Negative
**Priority**: P1
**Preconditions**: Authenticated; user has < 9 bookings (see TC-409 for the case at the limit)
**Steps**:
1. `POST /api/bookings` with `eventId: 99999999`
**Expected Results**: 404 "Event with id 99999999 not found"
**Business Rule**: Event lookup → NotFoundError
**Suggested Layer**: API

---

### TC-304: Create booking with missing required fields returns 400
**Category**: Negative
**Priority**: P1
**Preconditions**: Authenticated
**Steps**:
1. `POST /api/bookings` with an empty body `{}`
**Expected Results**: 400 `{ success: false, error: "Validation failed", details: [...] }`. `details` has an entry for each of `eventId`, `customerName`, `customerEmail`, `customerPhone`, `quantity`
**Business Rule**: `validateCreateBooking`
**Suggested Layer**: API

---

### TC-305: Create booking with quantity = 0 or negative returns 400
**Category**: Negative
**Priority**: P1
**Preconditions**: Authenticated
**Steps**:
1. `quantity: 0`
2. `quantity: -1`
3. `quantity: 1.5`
4. `quantity: "abc"`
**Expected Results**: All return 400; details message "Quantity must be an integer between 1 and 10"
**Business Rule**: `isInt({ min: 1, max: 10 })`
**Suggested Layer**: API

---

### TC-306: Create booking with quantity > 10 returns 400
**Category**: Negative
**Priority**: P1
**Preconditions**: Authenticated
**Steps**:
1. `quantity: 11`
**Expected Results**: 400 with the same quantity message
**Business Rule**: max 10
**Suggested Layer**: API

---

### TC-307: Cancel a booking that has already been cancelled returns 404
**Category**: Negative
**Priority**: P1
**Preconditions**: Authenticated; booking exists
**Steps**:
1. `DELETE /api/bookings/:id`, which returns 200 `{ success: true, message: "Booking cancelled" }`
2. Repeat the same request
**Expected Results**: The second call returns 404 "Booking with id X not found"
**Business Rule**: `cancelBooking` → `findById`
**Suggested Layer**: API

---

### TC-308: Bookings page shows error state when server is unreachable
**Category**: Negative
**Priority**: P2
**Preconditions**: `GET /api/bookings` fails (route-mock a 500 or abort)
**Steps**:
1. Open `/bookings`
**Expected Results**: "Couldn't load bookings", "Failed to connect to the server. Please try again.", and a "Retry" button. `/admin/bookings` shows "Failed to reach the server. Please retry."
**Business Rule**: `isError` branch
**Suggested Layer**: E2E (mocked) / Component

---

### TC-309 🆕: Non-numeric booking ID
**Category**: Negative
**Priority**: P2
**Preconditions**: Authenticated
**Steps**:
1. `GET /api/bookings/abc`
2. `DELETE /api/bookings/abc`
3. Open `/bookings/abc` in the UI
**Expected Results**: Expected 400 or 404 and the "Booking not found" UI. **Risk**: `Number('abc')` is `NaN`, which Prisma rejects, so this may return a **500 "Internal server error"**. There is no param validator on `:id`
**Business Rule**: Discovered code behavior — `findByIdOnly(Number(id))`
**Suggested Layer**: API

---

### TC-310 🆕: Booking ref lookup — not found and malformed
**Category**: Negative
**Priority**: P2
**Preconditions**: Authenticated
**Steps**:
1. `GET /api/bookings/ref/Z-000000` (does not exist)
2. `GET /api/bookings/ref/<own ref in lowercase>`
**Expected Results**: (1) 404 `Booking with reference "Z-000000" not found`. (2) Record the behavior: MySQL's default collation is case-insensitive, so a lowercase ref may still match. Decide whether that is intended
**Business Rule**: `getBookingByRef`
**Suggested Layer**: API

---

### TC-311 🆕: Customer field validation on booking create
**Category**: Negative
**Priority**: P1
**Preconditions**: Authenticated
**Steps**: `POST /api/bookings` with otherwise valid data, varying one field at a time:
1. `customerName: "A"`
2. `customerEmail: "not-an-email"`
3. `customerPhone: "12345"`
4. `customerPhone: "98765abcde"`
5. `eventId: 0`
**Expected Results**: Each returns 400 "Validation failed" with the matching message: "Customer name must be at least 2 characters", "Customer email must be a valid email address", "Customer phone must be at least 10 digits", "Customer phone must contain only digits and +, -, spaces, or parentheses", "Event ID must be a positive integer"
**Business Rule**: `bookingValidator.js`
**Suggested Layer**: API

---

### TC-312 🆕: Cancel in UI when the booking is already gone shows an error toast
**Category**: Negative
**Priority**: P2
**Preconditions**: Booking open on `/bookings/:id` in tab 1
**Steps**:
1. Delete the booking in tab 2 (or via API)
2. In tab 1, click "Cancel Booking", then "Yes, cancel it"
**Expected Results**: An error toast shows "Booking with id X not found"; the dialog closes; there is no redirect. The same happens from a list card
**Business Rule**: `onError` → `toast(err.message, 'error')`
**Suggested Layer**: E2E

---

### TC-313 🆕: Clear-all UI authenticates; unauthenticated clear is rejected
**Category**: Negative
**Priority**: P0
**Preconditions**: User is logged in and has at least two bookings
**Steps**:
1. Capture the `DELETE /api/bookings` request sent by the "Clear all bookings" action
2. Confirm the action; then create one booking again using the authenticated API
3. Send `DELETE /api/bookings` without an `Authorization` header
4. `GET /api/bookings` with the user's valid JWT
**Expected Results**: The UI request includes `Authorization: Bearer <valid JWT>`, returns 200, and clears the user's bookings. The unauthenticated DELETE returns 401 and does not remove the recreated booking.
**Business Rule**: `bookingsApi.clearAllBookings` uses the Axios request interceptor to attach the saved JWT; `authMiddleware` protects all booking routes and clear-all is scoped to the authenticated user
**Suggested Layer**: API / E2E

---

## Edge Cases

### TC-400: Exactly 9 bookings — adding a 10th prunes oldest from a DIFFERENT event (preferred)
**Category**: Edge Case
**Priority**: P0
**Preconditions**: 9 bookings. The oldest is for event A, and the second-oldest is also for a non-X event
**Steps**:
1. Create a 10th booking for event X, where at least one existing booking is also for X but is not the oldest
**Expected Results**: The count stays at 9; the oldest booking whose event is not X is removed; existing bookings for X are kept
**Business Rule**: `findOldestUserBookingExcludingEvent`
**Suggested Layer**: API

---

### TC-401: 9 bookings all for one event — 10th uses same-event fallback
**Category**: Edge Case
**Priority**: P1
**Preconditions**: 9 bookings, all for event X
**Steps**:
1. Create a booking for X with quantity q
2. Re-fetch X
**Expected Results**: The oldest booking is removed and the new one created; DB `availableSeats` drops permanently by q (it does not recover after Clear all)
**Business Rule**: `sameEventFallback` → `decrementSeats`
**Suggested Layer**: API

---

### TC-402: Booking with quantity = 1 (minimum) — full happy path
**Category**: Edge Case
**Priority**: P1
**Preconditions**: Event has seats
**Steps**:
1. Book with the default count of 1 (`#ticket-count`)
**Expected Results**: `quantity: 1`; `totalPrice = price`; the card shows "1 ticket" (singular)
**Business Rule**: quantity lower bound; card pluralization
**Suggested Layer**: E2E

---

### TC-403: Booking with quantity = 10 (maximum)
**Category**: Edge Case
**Priority**: P1
**Preconditions**: Event has ≥ 10 seats available to this user
**Steps**:
1. Click "+" until the count reaches 10, then click "+" once more
2. Confirm the booking
**Expected Results**: The count stops at 10; booking has `quantity: 10`, `totalPrice = price × 10`; the card shows "10 tickets"
**Business Rule**: quantity upper bound
**Suggested Layer**: E2E

---

### TC-404: Refund eligibility boundary — quantity = 2 is NOT eligible
**Category**: Edge Case
**Priority**: P1
**Preconditions**: Booking with quantity = 2
**Steps**:
1. Check refund eligibility
**Expected Results**: "Not eligible for refund. Group bookings (2 tickets) are non-refundable."
**Business Rule**: Rule 8 boundary
**Suggested Layer**: E2E / Component

---

### TC-405: Booking reference uniqueness — collision retry and fallback
**Category**: Edge Case
**Priority**: P2
**Preconditions**: `findByRef` mocked to always return an existing booking
**Steps**:
1. Call `generateUniqueRef("Tech")`
**Expected Results**: After 10 attempts, the function returns the fallback `T-<base36 timestamp, uppercase, last 8 chars>`. That suffix is 8 characters, so it does not match the documented `X-XXXXXX` 6-character pattern
**Business Rule**: `generateUniqueRef`
**Suggested Layer**: Unit

---

### TC-406: Clear all bookings when only one booking exists
**Category**: Edge Case
**Priority**: P2
**Preconditions**: Exactly 1 booking
**Steps**:
1. `DELETE /api/bookings`, or use the UI button
**Expected Results**: 200 `{ success: true, message: "1 booking(s) cleared" }`; the UI shows "No bookings yet"
**Business Rule**: `clearAllBookings` returns `deleted` count → controller message
**Suggested Layer**: API / E2E

---

### TC-407: Pagination on bookings list (API) — page 2 with partial results
**Category**: Edge Case
**Priority**: P2
**Preconditions**: 7 bookings
**Steps**:
1. `GET /api/bookings?page=2&limit=5`
**Expected Results**: 2 items; `pagination: { total: 7, page: 2, limit: 5, totalPages: 2 }`
**Business Rule**: `getBookings` pagination
**Suggested Layer**: API

---

### TC-408: Event title starting with a digit or lowercase letter — booking ref prefix
**Category**: Edge Case
**Priority**: P2
**Preconditions**: Dynamic events titled "100 Days Fest" and "jazz night"
**Steps**:
1. Book each event
**Expected Results**: The refs start with "1-" and "J-" respectively
**Business Rule**: `randomRef` `toUpperCase()`
**Suggested Layer**: API / Unit

---

### TC-409 🆕: At the 9-booking limit, a failed booking still deletes the oldest booking
**Category**: Edge Case
**Priority**: P1
**Preconditions**: Exactly 9 bookings; note the oldest booking's ID
**Steps**:
1. `POST /api/bookings` with `eventId: 99999999`, which returns 404
2. `POST /api/bookings` with a quantity above the available seats, which returns 400
3. `GET /api/bookings`
**Expected Results**: Expected behavior: a rejected request leaves all 9 bookings. **Likely actual**: each failed request deletes a booking (total drops to 8, then 7) because pruning runs before the event and seat checks and is not in a transaction. Validation errors (400 from the validator) do not prune, because they fail before the service runs
**Business Rule**: Discovered code behavior — order of operations in `createBooking`
**Suggested Layer**: API

---

### TC-410 🆕: Clear all with zero bookings
**Category**: Edge Case
**Priority**: P3
**Preconditions**: 0 bookings
**Steps**:
1. `DELETE /api/bookings`
**Expected Results**: 200 "0 booking(s) cleared"; no error
**Business Rule**: `deleteMany` on an empty set
**Suggested Layer**: API

---

### TC-411 🆕: Dismissing the "Clear all" browser confirm does nothing
**Category**: Edge Case
**Priority**: P1
**Preconditions**: User has bookings
**Steps**:
1. Register `page.once('dialog', d => d.dismiss())`
2. Click "Clear all bookings"
**Expected Results**: No DELETE request is sent; all cards remain; the button still reads "Clear all bookings"
**Business Rule**: `if (!confirm(...)) return;`
**Suggested Layer**: E2E

---

### TC-412 🆕: Out-of-range `?page=` shows a misleading empty state
**Category**: Edge Case
**Priority**: P3
**Preconditions**: User has 3 bookings
**Steps**:
1. Open `/bookings?page=5`
**Expected Results**: Record the behavior: the API returns `data: []` with `total: 3`, so the page shows "No bookings yet" even though bookings exist, and no pagination appears (totalPages = 1). Suggest falling back to page 1
**Business Rule**: Discovered code behavior — empty-state check uses `bookings.length === 0`
**Suggested Layer**: E2E / Component

---

### TC-413 🆕: Invalid pagination params
**Category**: Edge Case
**Priority**: P3
**Preconditions**: Authenticated
**Steps**:
1. `?page=0&limit=0`
2. `?page=-1`
3. `?limit=-5`
4. `?limit=abc`
**Expected Results**: (1) and (4) fall back to page 1 / limit 10, because `Number(x) || default`. (2) and (3) produce a negative `skip`/`take`. Expect 400, but record the actual behavior (likely 500 or odd results)
**Business Rule**: `getBookings` param coercion
**Suggested Layer**: API

---

### TC-414 🆕: Deleting a user-created event cascades to its bookings
**Category**: Edge Case
**Priority**: P1
**Preconditions**: User owns dynamic event E and has a booking for it with ID b
**Steps**:
1. Delete E from `/admin/events`
2. Open `/bookings` and `/bookings/b`
**Expected Results**: The booking is gone from the list; `/bookings/b` shows "Booking not found"
**Business Rule**: `Booking.event` `onDelete: Cascade`; Flow 5
**Suggested Layer**: E2E / API

---

### TC-415 🆕: Event FIFO pruning (7th event) cascades to bookings on the pruned event
**Category**: Edge Case
**Priority**: P2
**Preconditions**: User has 6 dynamic events; the oldest one has a booking
**Steps**:
1. Create a 7th event
2. Check bookings
**Expected Results**: The oldest event and its bookings are removed; the total booking count drops accordingly
**Business Rule**: `MAX_USER_DYNAMIC_EVENTS = 6` + cascade
**Suggested Layer**: API

---

### TC-416 🆕: Opening a booking that FIFO pruning removed
**Category**: Edge Case
**Priority**: P2
**Preconditions**: Note the ID of the oldest booking; user has 9 bookings
**Steps**:
1. Create a 10th booking
2. Open `/bookings/<oldest id>`
**Expected Results**: "Booking not found" (404). It must not show "Access Denied"
**Business Rule**: FIFO pruning; 404 vs 403 branching
**Suggested Layer**: E2E

---

### TC-417 🆕: Price display rounds to whole dollars
**Category**: Edge Case
**Priority**: P3
**Preconditions**: Dynamic event priced $99.50; book quantity 3
**Steps**:
1. View the card and the detail page
**Expected Results**: API `totalPrice` = 298.5. The UI shows "$299" total and "$100" per ticket (`maximumFractionDigits: 0`). Confirm with product that rounding is acceptable, since the totals displayed don't reconcile (3 × $100 ≠ $299)
**Business Rule**: Rule 9; `fmt_price`
**Suggested Layer**: Component / E2E

---

### TC-418 🆕: Double-submitting the cancel confirm
**Category**: Edge Case
**Priority**: P3
**Preconditions**: On a booking detail page; network throttled
**Steps**:
1. Open the cancel dialog, click "Yes, cancel it", then click it again quickly
**Expected Results**: Only one DELETE is sent, because the confirm button is in a loading state. There is one success toast and no 404 error toast
**Business Rule**: `ConfirmDialog` `loading={isPending}`
**Suggested Layer**: E2E

---

## UI State

### TC-500: Bookings list shows skeleton loading state while fetching
**Category**: UI State
**Priority**: P2
**Preconditions**: Delay the `GET /api/bookings` response (route mock)
**Steps**:
1. Open `/bookings`
**Expected Results**: 5 pulsing skeleton cards appear; no `booking-card` elements until the data arrives
**Business Rule**: `isLoading` branch
**Suggested Layer**: Component / E2E (mocked)

---

### TC-501: Bookings list shows empty state when user has no bookings
**Category**: UI State
**Priority**: P1
**Preconditions**: 0 bookings
**Steps**:
1. Open `/bookings`
**Expected Results**: "No bookings yet", "You haven't booked any events yet. Browse upcoming events and grab your tickets!", and a "Browse Events" button that navigates to `/events`
**Business Rule**: empty branch
**Suggested Layer**: E2E

---

### TC-502: Booking detail page shows loading spinner while fetching
**Category**: UI State
**Priority**: P3
**Preconditions**: Delay `GET /api/bookings/:id`
**Steps**:
1. Open `/bookings/:id`
**Expected Results**: A large centered spinner shows; no detail sections render until the data arrives
**Business Rule**: `isLoading` branch
**Suggested Layer**: Component

---

### TC-503: Cancel booking confirmation dialog content
**Category**: UI State
**Priority**: P1
**Preconditions**: Booking with ref R and quantity q
**Steps**:
1. Click "Cancel Booking" on the detail page
2. Click "Cancel Booking" on the list card
**Expected Results**: Both dialogs are titled "Cancel this booking?" with "Cancel" and "Yes, cancel it" buttons. Detail page text: "Cancelling R will release q seat(s) back to the event. This cannot be undone." Card text: "This will cancel R and release q seat(s) back to the event. This action cannot be undone."
**Business Rule**: Two-step confirmation
**Suggested Layer**: E2E / Component

---

### TC-504: Dismissing the cancel dialog does NOT cancel
**Category**: UI State
**Priority**: P1
**Preconditions**: On the detail page or list
**Steps**:
1. Open the cancel dialog
2. Click "Cancel" (or press Escape / click the backdrop, if Modal supports it)
**Expected Results**: The dialog closes; no DELETE request is sent; the booking remains
**Business Rule**: `onClose` only resets state
**Suggested Layer**: E2E

---

### TC-505: Booking detail breadcrumb displays the booking reference
**Category**: UI State
**Priority**: P3
**Preconditions**: Valid booking detail page
**Steps**:
1. Read the breadcrumb
**Expected Results**: "My Bookings / R"; "My Bookings" is a link to `/bookings`
**Business Rule**: Breadcrumb
**Suggested Layer**: E2E

---

### TC-506: Cancel booking success — toast and redirect
**Category**: UI State
**Priority**: P0
**Preconditions**: On the detail page; cancellation confirmed
**Steps**:
1. Confirm cancellation
**Expected Results**: Toast "Booking cancelled successfully"; URL changes to `/bookings`
**Business Rule**: detail `onSuccess` → `router.push('/bookings')`
**Suggested Layer**: E2E

---

### TC-507: "Clear all bookings" shows "Clearing…" while in progress
**Category**: UI State
**Priority**: P3
**Preconditions**: Delay `DELETE /api/bookings` (route mock)
**Steps**:
1. Click "Clear all bookings" and accept the confirm
**Expected Results**: The button reads "Clearing…" and is disabled. It returns to "Clear all bookings" afterwards, even if the request fails
**Business Rule**: `clearing` state with `finally`
**Suggested Layer**: Component / E2E (mocked)

---

### TC-508: Refund eligibility state machine
**Category**: UI State
**Priority**: P2
**Preconditions**: Detail page, idle refund state
**Steps**:
1. Click "Check eligibility for refund?" and wait for the result
2. Reload the page
**Expected Results**: The button is replaced by the spinner, then by the result. The button does not come back after the result. After a reload, the state resets to idle because nothing is persisted
**Business Rule**: idle → checking → eligible/ineligible
**Suggested Layer**: Component / E2E

---

### TC-509: Booking detail shows "Access Denied" (not "Not found") for 403
**Category**: UI State
**Priority**: P0
**Preconditions**: Route-mock `GET /api/bookings/:id` to return 403, or use a real cross-user ID
**Steps**:
1. Open `/bookings/:id`
**Expected Results**: The "Access Denied" state shows; neither the "Cancel Booking" button nor the refund widget is present
**Business Rule**: `error.status === 403` branch
**Suggested Layer**: E2E / Component

---

### TC-510: Pagination controls on `/bookings` — render only when totalPages > 1
**Category**: UI State
**Priority**: P3
**Preconditions**: Route-mock `GET /api/bookings` to return `pagination.totalPages = 3`, since this can't be reached with real data (cap of 9 vs page size 10)
**Steps**:
1. Open `/bookings`; click "2"; click "← Prev"
**Expected Results**: Controls render. Clicking a page updates the URL to `?page=2` and requests `page=2`. "← Prev" is disabled on page 1. No controls appear when `totalPages ≤ 1`
**Business Rule**: `Pagination` returns null when `totalPages <= 1`
**Suggested Layer**: Component / E2E (mocked)

---

### TC-511 🆕: "Cancel Booking" button visibility depends on status
**Category**: UI State
**Priority**: P3
**Preconditions**: Route-mock one booking with `status: "cancelled"` (see TC-113; this status can't be produced with real data)
**Steps**:
1. View the list card, the detail page, and the admin row
**Expected Results**: No Cancel button appears for the cancelled booking; it has a red "cancelled" badge. Confirmed bookings show the button and a green badge
**Business Rule**: `booking.status === 'confirmed'` guards
**Suggested Layer**: Component

---

### TC-512 🆕: Confirm dialog is locked while cancellation is in flight
**Category**: UI State
**Priority**: P3
**Preconditions**: Delay `DELETE /api/bookings/:id`
**Steps**:
1. Confirm a cancel; while the request is pending, try "Cancel", Escape, or a backdrop click
**Expected Results**: The confirm button shows a loading state; the "Cancel" button is disabled; the dialog can't be dismissed until the request completes
**Business Rule**: `ConfirmDialog` — `onClose` undefined and buttons disabled when `isLoading`
**Suggested Layer**: Component

---

### TC-513 🆕: Ticket pluralization on booking card
**Category**: UI State
**Priority**: P3
**Preconditions**: Bookings with quantity 1 and quantity 2
**Steps**:
1. Open `/bookings`
**Expected Results**: The cards show "🎫 1 ticket" and "🎫 2 tickets"
**Business Rule**: `BookingCard` plural logic
**Suggested Layer**: Component

---

### TC-514 🆕: Admin bookings — empty state, count, and filter reset
**Category**: UI State
**Priority**: P3
**Preconditions**: (a) 0 bookings; (b) bookings on page 2 of admin (requires a mock, limit 15)
**Steps**:
1. Open `/admin/bookings` with 0 bookings
2. With a mocked multi-page response, go to page 2, then change the status filter
**Expected Results**: (1) "No bookings found", "There are no bookings matching your filters." and "0 total bookings". (2) Changing the filter resets to page 1
**Business Rule**: `setStatus(...); setPage(1)`
**Suggested Layer**: Component / E2E (mocked)

---

### TC-515 🆕: "Retry" on error state refetches and recovers
**Category**: UI State
**Priority**: P2
**Preconditions**: Route-mock the first `GET /api/bookings` to fail and the second to succeed
**Steps**:
1. Open `/bookings`; see the error state; click "Retry"
**Expected Results**: The booking cards render; the error state is gone
**Business Rule**: `refetch()` on Retry
**Suggested Layer**: E2E (mocked)

---

### TC-516 🆕: Booking-limit banner on `/bookings` (spec vs. implementation)
**Category**: UI State
**Priority**: P3
**Preconditions**: User has 8–9 bookings
**Steps**:
1. Open `/bookings`
2. Open `/events`
**Expected Results**: The domain skill (business-rules §5) says `/bookings` shows a conditional booking-limit banner. The code has no such banner on `/bookings`. Only `/events` shows "Your sandbox holds up to 9 bookings…". Raise with product: either add the banner or correct the domain doc
**Business Rule**: Business-rules §5 vs `frontend/app/bookings/page.tsx`
**Suggested Layer**: E2E
