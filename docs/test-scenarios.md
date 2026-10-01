# Test Scenarios: Booking Management

Scope: booking creation, viewing (list + detail), cancellation, clear-all, refund eligibility,
FIFO pruning, cross-user access, and related UI states. Derived from
`.claude/references/eventhub-domain/business-rules.md`, `user-flows.md`, `api-reference.md`, plus
direct inspection of `backend/src/services/bookingService.js`,
`backend/src/validators/bookingValidator.js`, `frontend/app/bookings/page.tsx`,
`frontend/app/bookings/[id]/page.tsx`, and `frontend/app/events/[id]/page.tsx`.

---

## Happy Path (TC-001–099)

### TC-001: Book a single ticket for a static event

**Category**: Happy Path
**Priority**: P0
**Preconditions**: User logged in; at least one event with availableSeats > 0
**Steps**:

1. Navigate to `/events`, click "Book Now" on an available event
2. Fill customer name, email, phone; leave quantity at 1
3. Click "Confirm Booking"
   **Expected Results**: Confirmation card shown with booking ref, customer name, quantity=1, total = event.price; POST `/api/bookings` returns 201 with `message: "Booking confirmed!"`
   **Business Rule**: `bookingService.createBooking` (business-rules.md §1, §9)
   **Suggested Layer**: E2E

### TC-002: View bookings list showing a newly created booking

**Category**: Happy Path
**Priority**: P0
**Preconditions**: One booking exists for the user
**Steps**:

1. Navigate to `/bookings`
   **Expected Results**: Booking card visible with booking ref, event title, status "confirmed"
   **Business Rule**: user-flows.md Flow 4
   **Suggested Layer**: E2E

### TC-003: View booking detail page shows all sections

**Category**: Happy Path
**Priority**: P0
**Preconditions**: One booking exists
**Steps**:

1. From `/bookings`, click "View Details" on a booking card
   **Expected Results**: Breadcrumb + header show booking ref and "confirmed" badge; Event Details, Customer Details, Payment Summary, Refund, and Booking Information sections all render with correct data
   **Business Rule**: `frontend/app/bookings/[id]/page.tsx`
   **Suggested Layer**: E2E

### TC-004: Cancel a booking from detail page

**Category**: Happy Path
**Priority**: P0
**Preconditions**: One confirmed booking exists
**Steps**:

1. Open booking detail page
2. Click "Cancel Booking"
3. Confirm in the dialog ("Yes, cancel it")
   **Expected Results**: Success toast "Booking cancelled successfully"; redirected to `/bookings`; booking no longer listed
   **Business Rule**: business-rules.md §1, §4
   **Suggested Layer**: E2E

### TC-005: Clear all bookings

**Category**: Happy Path
**Priority**: P0
**Preconditions**: At least one booking exists
**Steps**:

1. On `/bookings`, click "Clear all bookings"
2. Accept the browser confirm dialog
   **Expected Results**: All bookings removed; "No bookings yet" empty state shown
   **Business Rule**: business-rules.md §4
   **Suggested Layer**: E2E

### TC-006: Navigate to bookings list via "View My Bookings" link

**Category**: Happy Path
**Priority**: P1
**Preconditions**: User just completed a booking (confirmation card visible)
**Steps**:

1. Click "View My Bookings" on the confirmation card
   **Expected Results**: Redirected to `/bookings`; new booking appears in list
   **Business Rule**: user-flows.md Flow 3
   **Suggested Layer**: E2E

### TC-007: Book multiple tickets and verify total price

**Category**: Happy Path
**Priority**: P0
**Preconditions**: Event with availableSeats >= 5
**Steps**:

1. Open event detail page, increment quantity to e.g. 3 via "+"
2. Fill form, confirm booking
   **Expected Results**: Confirmation shows quantity=3, total = event.price × 3; detail page "Payment Summary" reflects same total
   **Business Rule**: business-rules.md §9 (totalPrice = price × quantity)
   **Suggested Layer**: E2E

### TC-008: Refund eligibility — single ticket is eligible

**Category**: Happy Path
**Priority**: P1
**Preconditions**: Booking with quantity = 1
**Steps**:

1. On booking detail page, click "Check eligibility for refund?"
2. Wait for spinner to resolve (~4s)
   **Expected Results**: Result shows "Eligible for refund. Single-ticket bookings qualify for a full refund."
   **Business Rule**: business-rules.md §8
   **Suggested Layer**: E2E

### TC-009: Refund eligibility — multi-ticket is not eligible

**Category**: Happy Path
**Priority**: P1
**Preconditions**: Booking with quantity > 1
**Steps**:

1. On booking detail page, click "Check eligibility for refund?"
2. Wait for spinner to resolve
   **Expected Results**: Result shows "Not eligible for refund. Group bookings (N tickets) are non-refundable."
   **Business Rule**: business-rules.md §8
   **Suggested Layer**: E2E

### TC-010: Fetch booking by reference via API

**Category**: Happy Path
**Priority**: P2
**Preconditions**: Booking exists with known `bookingRef`
**Steps**:

1. `GET /api/bookings/ref/:ref` with owner's bearer token
   **Expected Results**: 200, returns booking matching the ref
   **Business Rule**: api-reference.md (Bookings)
   **Suggested Layer**: API

### TC-011: Paginate bookings list across multiple pages

**Category**: Happy Path
**Priority**: P2
**Preconditions**: User has more bookings than one page size (limit=10 per `bookingRepository.findAll`)
**Steps**:

1. Create 11+ bookings
2. Navigate to `/bookings`, go to page 2
   **Expected Results**: Page 1 shows first 10 (newest first, `orderBy createdAt desc`), page 2 shows the remainder
   **Business Rule**: `bookingRepository.findAll` (limit=10 default)
   **Suggested Layer**: E2E

---

## Business Rules (TC-100–199)

### TC-100: Booking reference first char matches event title first char

**Category**: Business Rule
**Priority**: P0
**Preconditions**: Book any event, note its title
**Steps**:

1. Complete a booking
2. Inspect `bookingRef`
   **Expected Results**: `bookingRef[0] === eventTitle[0].toUpperCase()`
   **Business Rule**: business-rules.md §7; `bookingService.randomRef`
   **Suggested Layer**: E2E / Unit

### TC-101: Booking reference matches format `[LETTER]-[6 alphanumeric]`

**Category**: Business Rule
**Priority**: P1
**Preconditions**: Booking created
**Steps**:

1. Inspect `bookingRef` value
   **Expected Results**: Matches `^[A-Z0-9]-[A-Z0-9]{6}$`
   **Business Rule**: business-rules.md §7
   **Suggested Layer**: Unit

### TC-102: totalPrice equals price × quantity on detail page

**Category**: Business Rule
**Priority**: P1
**Preconditions**: Booking with quantity > 1
**Steps**:

1. Open booking detail, compare "Price per ticket" × "Tickets" vs "Total Paid"
   **Expected Results**: Total Paid = price per ticket × tickets, exactly
   **Business Rule**: business-rules.md §9
   **Suggested Layer**: E2E

### TC-103: Seat count reduces immediately on booking

**Category**: Business Rule
**Priority**: P0
**Preconditions**: Event with known availableSeats (static event)
**Steps**:

1. Note availableSeats on event detail page
2. Book N tickets
3. Reload event detail page
   **Expected Results**: availableSeats decreased by N
   **Business Rule**: business-rules.md §1, §6
   **Suggested Layer**: E2E

### TC-104: Seat count restores on cancellation

**Category**: Business Rule
**Priority**: P0
**Preconditions**: Booking exists reducing an event's availableSeats
**Steps**:

1. Cancel the booking
2. Reload event detail page
   **Expected Results**: availableSeats restored (for dynamic events, computed as totalSeats − sum(remaining booking quantities); the deleted booking no longer counts)
   **Business Rule**: business-rules.md §6; `bookingService.cancelBooking`
   **Suggested Layer**: E2E

### TC-105: FIFO pruning deletes oldest booking at the 9-booking limit (different event)

**Category**: Business Rule
**Priority**: P0
**Preconditions**: User has exactly 9 bookings, all for events other than the one about to be booked
**Steps**:

1. Book a 10th ticket for a new/different event
2. View `/bookings`
   **Expected Results**: Total booking count stays at 9; the previously-oldest booking (by `createdAt`) is gone; the new booking is present
   **Business Rule**: business-rules.md §4; `bookingService.createBooking` (`findOldestUserBookingExcludingEvent`)
   **Suggested Layer**: E2E / API

### TC-106: FIFO same-event fallback burns a seat instead of freeing one

**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has exactly 9 bookings, all for the SAME event as the new (10th) booking being created
**Steps**:

1. Book a 10th ticket for that same event
2. Inspect the event's `availableSeats` before and after
   **Expected Results**: Oldest booking for that event is deleted (freeing its quantity), but `eventRepository.decrementSeats` also permanently reduces availableSeats by the new booking's quantity — net seat count drops rather than staying flat, since `sameEventFallback` is true
   **Business Rule**: `bookingService.createBooking` lines 71-97 (undocumented in business-rules.md — discovered in code)
   **Suggested Layer**: API

### TC-107: Per-user seat computation for dynamic events is isolated per user

**Category**: Business Rule
**Priority**: P1
**Preconditions**: Two user accounts (Gmail, Yahoo); one user's dynamic event
**Steps**:

1. User A creates a dynamic event with totalSeats=10
2. User B books 3 tickets on that event (if visible/allowed) OR User A books 3 tickets, then re-checks availableSeats as computed for their own bookings only
   **Expected Results**: `personalAvailable = availableSeats - sum(that user's own booking quantities for the event)` — one user's bookings don't reduce another user's perceived availability beyond the shared DB `availableSeats` field
   **Business Rule**: business-rules.md §6
   **Suggested Layer**: API

### TC-108: Same user can book the same dynamic event multiple times

**Category**: Business Rule
**Priority**: P2
**Preconditions**: User owns a dynamic event with sufficient seats
**Steps**:

1. Book 2 tickets for the event
2. Book 2 more tickets for the same event
   **Expected Results**: Both bookings succeed (two separate booking records); computed availableSeats reflects cumulative quantity booked by this user
   **Business Rule**: business-rules.md §6
   **Suggested Layer**: API

### TC-109: "Clear All Bookings" removes every booking for the user in one action

**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has 3+ bookings across different events
**Steps**:

1. Click "Clear all bookings" and confirm
   **Expected Results**: `DELETE /api/bookings` removes all rows for that user (`deleteAllForUser`); response message states count cleared; bookings list is empty
   **Business Rule**: business-rules.md §4
   **Suggested Layer**: API

### TC-110: Refund eligibility boundary — quantity=1 vs quantity=2

**Category**: Business Rule
**Priority**: P1
**Preconditions**: Two bookings, one qty=1 and one qty=2
**Steps**:

1. Check refund eligibility on each
   **Expected Results**: qty=1 → eligible; qty=2 → not eligible (confirms `quantity === 1` is the exact threshold, not `<=` some other value)
   **Business Rule**: business-rules.md §8
   **Suggested Layer**: E2E

### TC-111: Booking reference uniqueness under collision retry

**Category**: Business Rule
**Priority**: P3
**Preconditions**: None (stress scenario)
**Steps**:

1. Create many bookings rapidly for events with the same first letter
   **Expected Results**: All `bookingRef` values are unique; `generateUniqueRef` retries up to 10 times, then falls back to a timestamp-suffixed ref still prefixed by the event title's first letter
   **Business Rule**: `bookingService.generateUniqueRef` (discovered in code)
   **Suggested Layer**: Unit

---

## Security (TC-200–299)

### TC-200: Cross-user access to booking detail page shows "Access Denied"

**Category**: Security
**Priority**: P0
**Preconditions**: User A has a booking; User B is a different logged-in account
**Steps**:

1. As User A, note the booking's numeric ID
2. Log out, log in as User B
3. Navigate directly to `/bookings/:userA_booking_id`
   **Expected Results**: UI shows "Access Denied — You are not authorized to view this booking"; API returns 403
   **Business Rule**: business-rules.md §2; `bookingService.getBookingById`
   **Suggested Layer**: E2E

### TC-201: Cross-user access via booking reference lookup is forbidden

**Category**: Security
**Priority**: P1
**Preconditions**: User A's booking ref known to User B
**Steps**:

1. As User B, `GET /api/bookings/ref/:userA_ref`
   **Expected Results**: 403 Forbidden, message "You do not own this booking"
   **Business Rule**: `bookingService.getBookingByRef`
   **Suggested Layer**: API

### TC-202: Cannot cancel another user's booking

**Category**: Security
**Priority**: P0
**Preconditions**: User A's booking; User B's token
**Steps**:

1. As User B, `DELETE /api/bookings/:userA_booking_id`
   **Expected Results**: 403 Forbidden; booking still exists and is still visible to User A
   **Business Rule**: `bookingService.cancelBooking`
   **Suggested Layer**: API

### TC-203: Unauthenticated access to bookings endpoints is rejected

**Category**: Security
**Priority**: P0
**Preconditions**: No/expired JWT
**Steps**:

1. `GET /api/bookings` with no Authorization header
2. Navigate to `/bookings` in browser with cleared auth state
   **Expected Results**: API returns 401 Unauthorized; UI redirects to `/login`
   **Business Rule**: api-reference.md (Missing auth token)
   **Suggested Layer**: API / E2E

### TC-204: Non-existent booking ID returns 404, not a server error

**Category**: Security
**Priority**: P1
**Preconditions**: Logged in
**Steps**:

1. Navigate to `/bookings/999999999` (ID that doesn't exist)
   **Expected Results**: "Booking not found" state shown, not a crash/500; API returns 404
   **Business Rule**: `bookingService.getBookingById` (NotFoundError)
   **Suggested Layer**: E2E

### TC-205: Booking creation against an event not visible to the requester

**Category**: Security
**Priority**: P2
**Preconditions**: User B owns a dynamic event; User A does not own it and it is not static
**Steps**:

1. As User A, `POST /api/bookings` with `eventId` = User B's private dynamic event
   **Expected Results**: 404 Not Found (event lookup is scoped to static events + the requester's own events, per `eventRepository.findById(id, userId)`), not a silent success
   **Business Rule**: `bookingService.createBooking` (discovered in code)
   **Suggested Layer**: API

---

## Negative / Error (TC-300–399)

### TC-300: Empty customer name blocks submission

**Category**: Negative
**Priority**: P1
**Preconditions**: On event detail booking form
**Steps**:

1. Leave "Full Name" blank, fill rest, submit
   **Expected Results**: Client-side error "Name must be at least 2 chars"; no API call made
   **Business Rule**: `BookingForm.validate` / `bookingValidator.js`
   **Suggested Layer**: E2E / Component

### TC-301: Invalid email format is rejected

**Category**: Negative
**Priority**: P1
**Preconditions**: On booking form
**Steps**:

1. Enter `not-an-email` in email field, submit
   **Expected Results**: "Enter a valid email" error shown; submission blocked
   **Business Rule**: `BookingForm.validate`; API-side `isEmail()` check
   **Suggested Layer**: E2E

### TC-302: Phone number under 10 digits is rejected

**Category**: Negative
**Priority**: P1
**Preconditions**: On booking form
**Steps**:

1. Enter a 9-digit phone number, submit
   **Expected Results**: "Enter a valid 10-digit phone" error shown
   **Business Rule**: business-rules.md (Booking model, customerPhone min 10 digits); `bookingValidator.js`
   **Suggested Layer**: E2E

### TC-303: Customer name of 1 character rejected at API layer

**Category**: Negative
**Priority**: P2
**Preconditions**: Valid token
**Steps**:

1. `POST /api/bookings` with `customerName: "A"`
   **Expected Results**: 400, validation details field=`customerName`, message "Customer name must be at least 2 characters"
   **Business Rule**: `bookingValidator.js`
   **Suggested Layer**: API

### TC-304: Booking quantity exceeding available seats is rejected server-side

**Category**: Negative
**Priority**: P0
**Preconditions**: Event with availableSeats = N
**Steps**:

1. Bypass client cap; `POST /api/bookings` with `quantity` > N
   **Expected Results**: 400 "Only N seat(s) available, but <quantity> requested"
   **Business Rule**: `bookingService.createBooking` (InsufficientSeatsError)
   **Suggested Layer**: API

### TC-305: Quantity of 0 is rejected

**Category**: Negative
**Priority**: P1
**Preconditions**: Valid token
**Steps**:

1. `POST /api/bookings` with `quantity: 0`
   **Expected Results**: 400, "Quantity must be an integer between 1 and 10"
   **Business Rule**: `bookingValidator.js` (`isInt({ min: 1, max: 10 })`)
   **Suggested Layer**: API

### TC-306: Quantity of 11 (exceeds max) is rejected

**Category**: Negative
**Priority**: P1
**Preconditions**: Valid token, event with 11+ seats available
**Steps**:

1. `POST /api/bookings` with `quantity: 11`
   **Expected Results**: 400, "Quantity must be an integer between 1 and 10"
   **Business Rule**: `bookingValidator.js`
   **Suggested Layer**: API

### TC-307: Booking a non-existent eventId returns 404

**Category**: Negative
**Priority**: P1
**Preconditions**: Valid token
**Steps**:

1. `POST /api/bookings` with `eventId: 999999999`
   **Expected Results**: 404 "Event with id 999999999 not found"
   **Business Rule**: `bookingService.createBooking`
   **Suggested Layer**: API

### TC-308: Booking a sold-out event is blocked

**Category**: Negative
**Priority**: P0
**Preconditions**: Event with availableSeats = 0
**Steps**:

1. Open event detail page — verify "Confirm Booking" button shows "Sold Out" and is disabled
2. Bypass UI, `POST /api/bookings` with quantity=1 for that event
   **Expected Results**: UI button disabled; API returns 400 Insufficient seats
   **Business Rule**: `BookingForm` (`soldOut` disables button); `bookingService.createBooking`
   **Suggested Layer**: E2E / API

### TC-309: Double-cancel of the same booking fails gracefully

**Category**: Negative
**Priority**: P2
**Preconditions**: One confirmed booking, opened in two contexts (e.g. two tabs/requests)
**Steps**:

1. Cancel the booking once (succeeds)
2. Attempt to cancel the same booking ID again
   **Expected Results**: Second call returns 404 "Booking with id X not found"; UI shows error toast, does not crash
   **Business Rule**: `bookingService.cancelBooking`
   **Suggested Layer**: API

### TC-310: Clear-all on an already-empty list is a no-op

**Category**: Negative
**Priority**: P3
**Preconditions**: No bookings exist
**Steps**:

1. Trigger "Clear all bookings" (or call `DELETE /api/bookings`) with zero bookings
   **Expected Results**: No error; response reports 0 deleted; UI stays on empty state
   **Business Rule**: `bookingService.clearAllBookings`
   **Suggested Layer**: API

### TC-311: Missing required field on create returns field-level validation errors

**Category**: Negative
**Priority**: P1
**Preconditions**: Valid token
**Steps**:

1. `POST /api/bookings` omitting `eventId`
   **Expected Results**: 400, `error: "Validation failed"`, `details` array includes field `eventId`, message "Event ID is required"
   **Business Rule**: `bookingValidator.js`
   **Suggested Layer**: API

### TC-312: Phone number with invalid characters is rejected

**Category**: Negative
**Priority**: P2
**Preconditions**: Valid token
**Steps**:

1. `POST /api/bookings` with `customerPhone: "call-me-maybe"`
   **Expected Results**: 400, "Customer phone must contain only digits and +, -, spaces, or parentheses"
   **Business Rule**: `bookingValidator.js` (regex `/^[0-9+\-\s()]+$/`)
   **Suggested Layer**: API

---

## Edge Cases (TC-400–499)

### TC-400: Book exactly the last available seat

**Category**: Edge Case
**Priority**: P1
**Preconditions**: Event with availableSeats = quantity being requested (e.g. 1 seat left, book 1)
**Steps**:

1. Book the exact remaining quantity
2. Reload event detail page
   **Expected Results**: Booking succeeds; event now shows "SOLD OUT" / availableSeats = 0
   **Business Rule**: business-rules.md §6 (boundary of seat availability check)
   **Suggested Layer**: E2E

### TC-401: Book the maximum allowed quantity (10)

**Category**: Edge Case
**Priority**: P2
**Preconditions**: Event with availableSeats >= 10
**Steps**:

1. Increment quantity stepper to 10 (max), confirm booking
   **Expected Results**: Booking succeeds with quantity=10; "+" button disabled once at 10
   **Business Rule**: `bookingValidator.js` (`max: 10`); `BookingForm` (`maxQty`)
   **Suggested Layer**: E2E

### TC-402: Quantity stepper caps at availableSeats when fewer than 10 remain

**Category**: Edge Case
**Priority**: P2
**Preconditions**: Event with availableSeats = 3
**Steps**:

1. Open event detail page, click "+" repeatedly
   **Expected Results**: Stepper stops at 3 (not 10); "+" button disabled at 3; hint text shows "(max 3)"
   **Business Rule**: `BookingForm` (`maxQty = Math.min(10, event.availableSeats)`)
   **Suggested Layer**: E2E

### TC-403: 10th booking for a different event FIFO-deletes the oldest booking

**Category**: Edge Case
**Priority**: P1
**Preconditions**: Exactly 9 existing bookings for various events
**Steps**:

1. Create a 10th booking for an event not among the existing 9
2. Refresh `/bookings`
   **Expected Results**: List still shows 9 bookings total; the ex-oldest booking is gone; the new one is present
   **Business Rule**: business-rules.md §4
   **Suggested Layer**: API

### TC-404: 10th booking for the same event as all 9 existing triggers same-event fallback

**Category**: Edge Case
**Priority**: P2
**Preconditions**: Exactly 9 existing bookings, all for the same event as the new one
**Steps**:

1. Create the 10th booking for that same event
   **Expected Results**: Oldest of the 9 is deleted, but the event's availableSeats is additionally decremented by the new booking's quantity (seat "burned"), per `sameEventFallback` logic
   **Business Rule**: `bookingService.createBooking` lines 71-97 (discovered in code)
   **Suggested Layer**: API

### TC-405: Event title with a non-letter first character

**Category**: Edge Case
**Priority**: P3
**Preconditions**: A dynamic event whose title starts with a digit or symbol (e.g. "24 Hour Hackathon")
**Steps**:

1. Book that event
   **Expected Results**: `bookingRef` prefix is that character uppercased (e.g. "2-XXXXXX"); if title is empty/undefined, falls back to "E" per `randomRef`'s `?? 'E'`
   **Business Rule**: `bookingService.randomRef` (discovered in code)
   **Suggested Layer**: Unit

### TC-406: Booking ref collision fallback after 10 retries

**Category**: Edge Case
**Priority**: P3
**Preconditions**: Simulate `findByRef` always returning a match (mocked)
**Steps**:

1. Trigger `generateUniqueRef` in a state where 10 consecutive collisions occur
   **Expected Results**: Falls back to `${prefix}-${Date.now() base36 suffix}`, still correctly prefixed, no infinite loop
   **Business Rule**: `bookingService.generateUniqueRef`
   **Suggested Layer**: Unit

### TC-407: Bookings pagination boundary at exactly 10 vs 11 bookings

**Category**: Edge Case
**Priority**: P2
**Preconditions**: User has exactly 10 bookings, then 11
**Steps**:

1. At 10 bookings, view `/bookings` — check for a page 2 control
2. Add an 11th booking, re-check
   **Expected Results**: At 10, only 1 page (no pagination controls, or single disabled page); at 11, a second page appears with 1 booking
   **Business Rule**: `bookingRepository.findAll` (limit=10) — note: business-rules.md §4 states "max 9 bookings shown"; actual code paginates at 10 — flag discrepancy if confirmed
   **Suggested Layer**: E2E

### TC-408: Cancelling the last booking flips list straight to empty state

**Category**: Edge Case
**Priority**: P1
**Preconditions**: Exactly one booking exists
**Steps**:

1. Cancel it from the detail page
   **Expected Results**: Redirected to `/bookings`, which now renders the "No bookings yet" empty state (not a stale/loading list)
   **Business Rule**: `frontend/app/bookings/page.tsx`
   **Suggested Layer**: E2E

### TC-409: Refund spinner duration is ~4 seconds, not instant or stuck

**Category**: Edge Case
**Priority**: P3
**Preconditions**: Any booking
**Steps**:

1. Click "Check eligibility for refund?"
2. Assert spinner visible immediately after click and still visible shortly before 4s
3. Assert result visible after ~4s
   **Expected Results**: Spinner shows for the full ~4000ms window (`setTimeout(..., 4000)`) before result renders
   **Business Rule**: `RefundEligibility` component (discovered in code)
   **Suggested Layer**: E2E

### TC-410: Phone number exactly at the 10-digit boundary

**Category**: Edge Case
**Priority**: P2
**Preconditions**: On booking form
**Steps**:

1. Enter exactly 10 digits → submit (should pass)
2. Enter 9 digits → submit (should fail)
   **Expected Results**: 10 digits accepted; 9 digits rejected with validation error
   **Business Rule**: business-rules.md (Booking.customerPhone min 10 digits)
   **Suggested Layer**: E2E

### TC-411: Customer name exactly at the 2-character boundary

**Category**: Edge Case
**Priority**: P2
**Preconditions**: On booking form
**Steps**:

1. Enter a 2-character name → submit (should pass)
2. Enter a 1-character name → submit (should fail)
   **Expected Results**: 2 chars accepted; 1 char rejected with "Name must be at least 2 chars"
   **Business Rule**: `bookingValidator.js` (`isLength({ min: 2 })`)
   **Suggested Layer**: E2E

---

## UI State (TC-500–599)

### TC-500: Bookings list shows loading skeletons while fetching

**Category**: UI State
**Priority**: P2
**Preconditions**: Navigate to `/bookings` (throttle network if needed to observe state)
**Steps**:

1. Navigate to `/bookings`
   **Expected Results**: 5 `BookingCardSkeleton` placeholders render before data resolves
   **Business Rule**: `frontend/app/bookings/page.tsx`
   **Suggested Layer**: Component

### TC-501: Bookings list error state with working Retry

**Category**: UI State
**Priority**: P2
**Preconditions**: Mock/force `GET /api/bookings` to fail
**Steps**:

1. Navigate to `/bookings` with the API mocked to return an error
2. Click "Retry" after fixing/unmocking the response
   **Expected Results**: "Couldn't load bookings" + Retry button shown on failure; clicking Retry successfully re-fetches and renders the list
   **Business Rule**: `frontend/app/bookings/page.tsx` (isError branch)
   **Suggested Layer**: Component / E2E with route mocking

### TC-502: Bookings list empty state with Browse Events CTA

**Category**: UI State
**Priority**: P1
**Preconditions**: No bookings exist
**Steps**:

1. Navigate to `/bookings`
   **Expected Results**: "No bookings yet" title, description, and a "Browse Events" button linking to `/events`
   **Business Rule**: `frontend/app/bookings/page.tsx`
   **Suggested Layer**: E2E

### TC-503: Booking detail page loading state shows spinner

**Category**: UI State
**Priority**: P3
**Preconditions**: Navigate to a valid booking detail URL
**Steps**:

1. Navigate to `/bookings/:id` (throttle network to observe)
   **Expected Results**: Centered large spinner shown before data resolves
   **Business Rule**: `frontend/app/bookings/[id]/page.tsx`
   **Suggested Layer**: Component

### TC-504: "Booking not found" state for invalid ID (non-403 case)

**Category**: UI State
**Priority**: P2
**Preconditions**: ID that doesn't exist at all (not a real other-user booking)
**Steps**:

1. Navigate to `/bookings/999999999`
   **Expected Results**: "Booking not found" (not "Access Denied") since the error isn't a 403; "View My Bookings" CTA present
   **Business Rule**: `frontend/app/bookings/[id]/page.tsx` (`is403` branch check)
   **Suggested Layer**: E2E

### TC-505: "Cancel Booking" button only shows for confirmed bookings

**Category**: UI State
**Priority**: P3
**Preconditions**: A booking record (all seeded/created bookings are status="confirmed" per business-rules.md §Booking model)
**Steps**:

1. View booking detail page
   **Expected Results**: "Cancel Booking" button renders since `booking.status === 'confirmed'`; conditional logic exists for other statuses even though none are currently reachable via the app
   **Business Rule**: `frontend/app/bookings/[id]/page.tsx` line 166
   **Suggested Layer**: Component

### TC-506: Clear-all button shows "Clearing…" disabled state mid-request

**Category**: UI State
**Priority**: P3
**Preconditions**: Bookings exist; network slow enough to observe transient state
**Steps**:

1. Click "Clear all bookings", confirm dialog
2. Observe button text/state immediately after confirming
   **Expected Results**: Button text changes to "Clearing…" and is disabled until the request resolves
   **Business Rule**: `frontend/app/bookings/page.tsx` (`clearing` state)
   **Suggested Layer**: Component

### TC-507: Cancel confirm dialog shows loading state while pending

**Category**: UI State
**Priority**: P3
**Preconditions**: On booking detail page, cancel dialog open
**Steps**:

1. Click "Yes, cancel it"
   **Expected Results**: `ConfirmDialog` shows loading state (`isPending` from `useCancelBooking`) until cancel completes
   **Business Rule**: `frontend/app/bookings/[id]/page.tsx`
   **Suggested Layer**: Component

### TC-508: Booking status badge reflects status correctly

**Category**: UI State
**Priority**: P3
**Preconditions**: Confirmed booking
**Steps**:

1. View booking detail page header
   **Expected Results**: Badge shows "confirmed" text with `success` (green) variant
   **Business Rule**: `frontend/app/bookings/[id]/page.tsx` line 160
   **Suggested Layer**: Component

### TC-509: Quantity stepper buttons disable at min/max boundaries

**Category**: UI State
**Priority**: P2
**Preconditions**: Event detail page open, availableSeats > 1
**Steps**:

1. At quantity=1, verify "−" is disabled
2. Increment to maxQty, verify "+" is disabled
   **Expected Results**: "−" disabled only at quantity=1; "+" disabled only at `maxQty = min(10, availableSeats)`
   **Business Rule**: `BookingForm` disabled props (lines 122, 129)
   **Suggested Layer**: Component

### TC-510: Sandbox warning banner on bookings page reflects booking count

**Category**: UI State
**Priority**: P2
**Preconditions**: Vary booking count from low (<5) to near the 9-booking limit
**Steps**:

1. With few bookings, view `/bookings` — banner should be hidden
2. With bookings count close to/at 9, view `/bookings` — banner should appear, referencing the sandbox limit
   **Expected Results**: Banner visibility is conditional on booking count threshold, per business-rules.md §5
   **Business Rule**: business-rules.md §5
   **Suggested Layer**: E2E
