# EventHub — Booking Management Test Scenarios

Generated: 2026-10-24
Scope: Booking list and detail, booking creation and lookup, cancellation, clear-all, refund eligibility, FIFO limits, per-user seats, admin bookings, API validation, and access control.

Sources: `.claude/skills/eventhub-domain/`; `backend/src/services/bookingService.js`, `backend/src/repositories/bookingRepository.js`, `backend/src/validators/bookingValidator.js`, `backend/src/routes/bookingRoutes.js`, `backend/src/middleware/authMiddleware.js`; `frontend/app/bookings/**`, `frontend/app/admin/bookings/page.tsx`, `frontend/components/bookings/BookingCard.jsx`, and `frontend/lib/hooks/useBookings.ts`.

## Observed differences from domain documentation

These scenarios assert actual implementation behavior where it differs from the domain docs:

| Area | Documented behavior | Observed code behavior | Covered by |
|---|---|---|---|
| Cross-user cancellation | Cross-user booking access returns 403 | `DELETE /api/bookings/:id` returns 404 because lookup is filtered by owner; the booking remains unchanged | TC-203 |
| Static-event seats | Static-event `availableSeats` is fixed | Per-user booked quantity is subtracted for static events too | TC-109 |
| Bookings banner | Bookings page has a conditional sandbox banner | No banner is rendered on `/bookings` | TC-511 |
| Clear-all visibility | Clear-all is described when bookings exist | The button is rendered even in the empty state | TC-501 |
| FIFO timing | Failed booking should not remove an existing booking | FIFO pruning happens before event and seat checks; a failed request at the limit can delete a booking | TC-110, TC-111 |
| Pagination | Bookings page displays up to 9 bookings | Page requests 10; with the 9-booking limit, real data cannot fill a second page | TC-510 |
| Booking reference fallback | Reference has a six-character suffix and is collision-free | After 10 collisions, fallback uses a timestamp suffix of up to eight characters and is not checked for uniqueness | TC-408 |

---

## Happy Path

### TC-001: View the bookings list
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is authenticated and owns at least one booking
**Steps**:
1. Open `/bookings` from the My Bookings navigation link.
**Expected Results**: The page heading is "My Bookings"; one card appears per owned booking. Each card shows its reference, confirmed status, ID, event title/date/city, ticket quantity, booking date, total, "View Details", and "Cancel Booking".
**Business Rule**: Flow 4 — manage bookings; users see their own bookings.
**Suggested Layer**: E2E

### TC-002: View booking details
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User owns a booking
**Steps**:
1. Open `/bookings` and select "View Details" on a booking card.
**Expected Results**: `/bookings/:id` shows the reference in the breadcrumb and header, event details, customer details, payment summary, refund section, and booking information including the booking ID.
**Business Rule**: Flow 4 — booking details.
**Suggested Layer**: E2E

### TC-003: Cancel a booking from its detail page
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User owns a confirmed booking
**Steps**:
1. Open its detail page, select "Cancel Booking", then confirm with "Yes, cancel it".
**Expected Results**: A "Booking cancelled successfully" toast appears, the user returns to `/bookings`, and the booking no longer appears or resolves via `GET /api/bookings/:id`.
**Business Rule**: Cancellation permanently deletes the booking.
**Suggested Layer**: E2E

### TC-004: Cancel a booking from its list card
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User owns at least two bookings
**Steps**:
1. On `/bookings`, select "Cancel Booking" on one card and confirm.
**Expected Results**: A success toast appears, the dialog closes, the cancelled card disappears, other bookings remain, and the user stays on `/bookings`.
**Business Rule**: Flow 4 — cancel an individual booking.
**Suggested Layer**: E2E

### TC-005: Clear all of the user's bookings
**Category**: Happy Path
**Priority**: P0
**Preconditions**: User owns at least two bookings
**Steps**:
1. Accept the native confirmation "Clear all your bookings? This cannot be undone." and select "Clear all bookings".
**Expected Results**: The list refreshes to "No bookings yet"; `GET /api/bookings` returns an empty `data` array and `pagination.total` of 0.
**Business Rule**: Clear All Bookings deletes all bookings owned by the authenticated user.
**Suggested Layer**: E2E + API

### TC-006: Open bookings from the booking confirmation
**Category**: Happy Path
**Priority**: P1
**Preconditions**: User is authenticated and can book an event with available seats
**Steps**:
1. Complete a booking and select "View My Bookings" on the confirmation.
**Expected Results**: The user reaches `/bookings`; the newly created booking is first and its reference matches the confirmation.
**Business Rule**: Flow 3 to Flow 4; bookings are ordered newest first.
**Suggested Layer**: E2E

### TC-007: Create a booking through the API
**Category**: Happy Path
**Priority**: P0
**Preconditions**: Valid JWT and a visible event with sufficient personal seats
**Steps**:
1. Submit `POST /api/bookings` with event ID, valid customer fields, and quantity 2.
**Expected Results**: Response is 201 with `success: true`, `message: "Booking confirmed!"`, confirmed status, calculated total price, an included event, and a generated booking reference.
**Business Rule**: Booking creation and price calculation.
**Suggested Layer**: API

### TC-008: Look up a booking by its reference
**Category**: Happy Path
**Priority**: P1
**Preconditions**: Valid JWT and a booking owned by the caller with a known reference
**Steps**:
1. Request `GET /api/bookings/ref/:ref` using the caller's bearer token.
**Expected Results**: Response is 200; `data.bookingRef` matches the requested reference and `data.event` is included.
**Business Rule**: Authenticated booking-reference lookup.
**Suggested Layer**: API

### TC-009: Manage bookings in the admin bookings view
**Category**: Happy Path
**Priority**: P2
**Preconditions**: User is authenticated and owns a booking
**Steps**:
1. Open `/admin/bookings`, view a row, open its "View" modal, then cancel a confirmed booking and confirm.
**Expected Results**: Table displays Ref, Customer, Event, Qty, Total, Status, Date, and Actions; modal title includes the booking reference; a "Booking cancelled" toast appears and the row is removed.
**Business Rule**: Admin bookings view uses the same current-user booking sandbox.
**Suggested Layer**: E2E

### TC-010: Return to the list from booking details
**Category**: Happy Path
**Priority**: P2
**Preconditions**: User is viewing an owned booking
**Steps**:
1. Select the "My Bookings" breadcrumb, then repeat using "← Back to My Bookings".
**Expected Results**: Both navigation controls open `/bookings`.
**Business Rule**: Flow 4 — booking navigation.
**Suggested Layer**: E2E

---

## Business Rules

### TC-100: The 10th booking replaces the oldest booking on a different event
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User has 9 bookings; the oldest is on Event A and the new booking will be on Event B
**Steps**:
1. Create a booking on Event B, then retrieve the user's bookings.
**Expected Results**: The total remains 9; the oldest Event A booking is gone, the new booking exists, and the other bookings remain.
**Business Rule**: Maximum 9 bookings; FIFO pruning prefers a booking on a different event.
**Suggested Layer**: API

### TC-101: FIFO prefers the oldest booking on a different event
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has 9 bookings; the oldest overall is on Event B, the next oldest is on Event A, and the new booking is for Event B
**Steps**:
1. Create another booking on Event B.
**Expected Results**: The Event A booking is pruned even though it is not the oldest overall; total remains 9.
**Business Rule**: The service selects the oldest booking excluding the requested event before falling back to the oldest overall.
**Suggested Layer**: API

### TC-102: Same-event FIFO fallback permanently reduces stored seats
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has 9 bookings, all on the same user-created event
**Steps**:
1. Create a 10th booking on that event and note its quantity.
2. Clear all bookings and retrieve the event's seat availability.
**Expected Results**: The booking succeeds and total bookings stay at 9. After clear-all, available seats remain reduced by the new booking quantity because this fallback decrements the event's stored seat count.
**Business Rule**: Same-event FIFO fallback burns seats in `bookingService.createBooking`.
**Suggested Layer**: API

### TC-103: Single-ticket booking is eligible for refund
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User owns a booking with quantity 1
**Steps**:
1. Open the booking detail page and select "Check eligibility for refund?".
**Expected Results**: After the check, the result states "Eligible for refund" and "Single-ticket bookings qualify for a full refund."
**Business Rule**: Quantity 1 is eligible; decision is client-side.
**Suggested Layer**: Component / E2E

### TC-104: Multi-ticket booking is not eligible for refund
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User owns a booking with quantity greater than 1
**Steps**:
1. Open the booking detail page and check refund eligibility.
**Expected Results**: Result states "Not eligible for refund" and reports the actual quantity as a non-refundable group booking.
**Business Rule**: Quantity greater than 1 is not eligible.
**Suggested Layer**: Component / E2E

### TC-105: Refund check displays its checking state before the result
**Category**: Business Rule
**Priority**: P2
**Preconditions**: User is viewing any booking detail page
**Steps**:
1. Select "Check eligibility for refund?" and observe the refund section until a result appears.
**Expected Results**: The checking message and spinner appear immediately; the result appears after approximately 4 seconds and replaces the spinner.
**Business Rule**: Client-side refund check has a 4-second checking state and no refund API.
**Suggested Layer**: Component

### TC-106: Total price is event price multiplied by quantity
**Category**: Business Rule
**Priority**: P0
**Preconditions**: Visible event has known price P and sufficient seats
**Steps**:
1. Book quantity N and compare API total with the booking card and detail page.
**Expected Results**: Stored total is P × N; the UI displays the total using USD formatting with zero fractional digits.
**Business Rule**: `totalPrice = event.price × quantity`.
**Suggested Layer**: API + E2E

### TC-107: Reference prefix is based on the event title
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User can book events whose titles start with uppercase and lowercase letters
**Steps**:
1. Book an event titled "Tech Conference" and one titled "summer fest".
**Expected Results**: References begin `T-` and `S-` respectively; ordinary generated refs have six uppercase alphanumeric characters after the hyphen.
**Business Rule**: Booking reference begins with the uppercase first character of the event title.
**Suggested Layer**: API + E2E

### TC-108: Cancelling restores per-user seats for a user-created event
**Category**: Business Rule
**Priority**: P0
**Preconditions**: User-created event has S seats and no bookings by the user
**Steps**:
1. Book 3 tickets and read the event.
2. Cancel the booking and read the event again.
**Expected Results**: User initially sees S−3 available seats and then S after cancellation.
**Business Rule**: Per-user availability is computed from that user's active booking quantities; cancellation deletes the booking.
**Suggested Layer**: API + E2E

### TC-109: Static event seat availability is also computed per user
**Category**: Business Rule
**Priority**: P1
**Preconditions**: Static event has availability A; two user accounts can access it
**Steps**:
1. User A books 2 tickets and reads the event.
2. User B reads the same event.
3. User A cancels and reads it again.
**Expected Results**: A sees A−2; B still sees A; after cancellation A sees A. This differs from the domain description that static-event availability is fixed.
**Business Rule**: `withPersonalSeats` computes per-user availability for all events.
**Suggested Layer**: API

### TC-110: Failed booking for a nonexistent event can prune an existing booking
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has exactly 9 bookings
**Steps**:
1. Submit a valid booking request for a positive event ID that does not exist.
2. Retrieve the user's booking list.
**Expected Results**: Request returns 404. The desired invariant is that all 9 existing bookings remain; current code prunes one before checking event existence, leaving 8. Report the observed data loss as a defect.
**Business Rule**: FIFO pruning should only remove a booking when a new booking succeeds.
**Suggested Layer**: API

### TC-111: Insufficient-seat failure can prune an existing booking
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has 9 bookings and requests more seats than available on a valid event
**Steps**:
1. Submit a valid booking request whose quantity exceeds that user's remaining seats.
2. Retrieve the user's booking list.
**Expected Results**: Request returns 400 with the remaining-seat count. The desired invariant is that the existing total remains 9; current code prunes before checking seats and leaves 8. Report the observed data loss as a defect.
**Business Rule**: A rejected booking should not cause FIFO replacement.
**Suggested Layer**: API

### TC-112: Booking list sorts newest first
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User can create at least three bookings in sequence
**Steps**:
1. Create bookings X, Y, then Z; request `GET /api/bookings` and open `/bookings`.
**Expected Results**: Both API and UI show Z, Y, X.
**Business Rule**: Repository orders bookings by `createdAt` descending.
**Suggested Layer**: API + E2E

### TC-113: Deleting an event cascades to associated bookings
**Category**: Business Rule
**Priority**: P1
**Preconditions**: User owns a dynamic event and a booking for that event
**Steps**:
1. Delete the event through event management.
2. Retrieve bookings and open the deleted booking's former detail URL.
**Expected Results**: The booking is absent from the list and detail page reports "Booking not found".
**Business Rule**: Event-to-booking relation uses cascade delete.
**Suggested Layer**: API + E2E

### TC-114: Clear-all only removes bookings owned by the caller
**Category**: Business Rule
**Priority**: P0
**Preconditions**: Users A and B each have at least one booking
**Steps**:
1. User A requests `DELETE /api/bookings`.
2. User B retrieves `GET /api/bookings`.
**Expected Results**: User A receives a success response with its deleted count; User B's bookings remain unchanged.
**Business Rule**: Repository clear-all deletion is scoped to authenticated `userId`.
**Suggested Layer**: API

### TC-115: Booking status remains confirmed; cancellation deletes the record
**Category**: Business Rule
**Priority**: P2
**Preconditions**: User owns a confirmed booking
**Steps**:
1. Request bookings filtered by `status=cancelled`.
2. Cancel the booking and request the same filter again.
**Expected Results**: Both results are empty; cancellation does not retain a cancelled record.
**Business Rule**: Booking status defaults to confirmed; cancellation deletes the row.
**Suggested Layer**: API

---

## Security

### TC-200: Another user's booking detail shows Access Denied
**Category**: Security
**Priority**: P0
**Preconditions**: User A owns a booking; User B has a separate account
**Steps**:
1. Authenticate as User B and open `/bookings/:id` for User A's booking.
**Expected Results**: Page shows "Access Denied" and "You are not authorized to view this booking." No booking data is displayed.
**Business Rule**: Booking details are isolated by user ownership.
**Suggested Layer**: E2E

### TC-201: Another user cannot retrieve booking details by ID
**Category**: Security
**Priority**: P0
**Preconditions**: User A owns booking X; User B has a valid bearer token
**Steps**:
1. User B requests `GET /api/bookings/X`.
**Expected Results**: Response is 403 with an authorization error and contains no booking fields.
**Business Rule**: Booking detail endpoint checks ownership.
**Suggested Layer**: API

### TC-202: Another user cannot retrieve a booking by reference
**Category**: Security
**Priority**: P1
**Preconditions**: User A owns a booking with reference R; User B has a valid token
**Steps**:
1. User B requests `GET /api/bookings/ref/R`.
**Expected Results**: Response is 403 with an ownership error and no booking data.
**Business Rule**: Booking reference lookup checks ownership.
**Suggested Layer**: API

### TC-203: Another user cannot cancel a booking
**Category**: Security
**Priority**: P0
**Preconditions**: User A owns booking X; User B has a valid token
**Steps**:
1. User B requests `DELETE /api/bookings/X`.
2. User A retrieves `GET /api/bookings/X`.
**Expected Results**: Delete returns 404 because cancellation lookup is owner-filtered; User A still retrieves the unchanged booking. This differs from the general documented cross-user 403 behavior.
**Business Rule**: Cancellation must not modify another user's booking.
**Suggested Layer**: API

### TC-204: Booking API requires a bearer token
**Category**: Security
**Priority**: P0
**Preconditions**: No authenticated session
**Steps**:
1. Call list, detail, reference lookup, create, cancel, and clear-all endpoints without an Authorization header and with a non-Bearer scheme.
**Expected Results**: Every request returns 401 Unauthorized and no data changes.
**Business Rule**: All booking routes use authentication middleware.
**Suggested Layer**: API

### TC-205: Invalid or expired token is rejected
**Category**: Security
**Priority**: P1
**Preconditions**: Ability to supply invalid, tampered, and expired JWTs
**Steps**:
1. Request a booking endpoint using each invalid token.
**Expected Results**: Each request returns 401 with "Invalid or expired token".
**Business Rule**: JWT verification in authentication middleware.
**Suggested Layer**: API

### TC-206: Unauthenticated booking pages redirect to login
**Category**: Security
**Priority**: P0
**Preconditions**: No valid login session
**Steps**:
1. Navigate directly to `/bookings`, then `/bookings/1`.
**Expected Results**: Both routes redirect to `/login` and reveal no booking data.
**Business Rule**: AuthGuard protects application pages.
**Suggested Layer**: E2E

### TC-207: Spoofed user ID cannot change booking ownership
**Category**: Security
**Priority**: P1
**Preconditions**: User A has a valid token; User B's ID is known
**Steps**:
1. User A creates a booking with User B's ID included as an extra `userId` field.
2. Retrieve bookings as both users.
**Expected Results**: Booking appears only in User A's list; ownership comes from the authenticated token.
**Business Rule**: Service uses `req.user.userId`, not client-supplied ownership.
**Suggested Layer**: API

### TC-208: Client-supplied derived booking fields are ignored
**Category**: Security
**Priority**: P1
**Preconditions**: Valid token and event with known price
**Steps**:
1. Create a booking with valid required data plus forged `totalPrice`, `status`, and `bookingRef`.
**Expected Results**: Server calculates total price, sets status to confirmed, and generates a reference with the event's title prefix.
**Business Rule**: Server-derived fields cannot be mass-assigned by the client.
**Suggested Layer**: API

### TC-209: User cannot book another user's private event
**Category**: Security
**Priority**: P1
**Preconditions**: User A owns a dynamic event; User B has a valid token
**Steps**:
1. User B submits a booking request for User A's event ID.
**Expected Results**: Request returns 404 and creates no booking.
**Business Rule**: Dynamic event visibility is limited to its owner.
**Suggested Layer**: API

### TC-210: Booking list never exposes another user's booking
**Category**: Security
**Priority**: P0
**Preconditions**: Users A and B each own bookings
**Steps**:
1. Retrieve the list as each user, including with event/status filters and pagination.
**Expected Results**: Every returned booking belongs to the caller; totals and pages reflect only that caller's data.
**Business Rule**: Booking repository list query is scoped to `userId`.
**Suggested Layer**: API

---

## Negative / Error

### TC-300: Nonexistent booking detail shows not found
**Category**: Negative
**Priority**: P1
**Preconditions**: Authenticated user; no booking with the chosen ID
**Steps**:
1. Open `/bookings/999999`.
**Expected Results**: Page shows "Booking not found", the explanatory message, and a "View My Bookings" action.
**Business Rule**: Missing booking maps to the detail page not-found state.
**Suggested Layer**: E2E

### TC-301: Nonexistent booking ID or reference returns 404
**Category**: Negative
**Priority**: P1
**Preconditions**: Valid bearer token
**Steps**:
1. Request a nonexistent booking ID and nonexistent booking reference.
**Expected Results**: Both return 404 with a booking-not-found error.
**Business Rule**: Missing records raise `NotFoundError`.
**Suggested Layer**: API

### TC-302: Nonnumeric booking ID is handled without a success response
**Category**: Negative
**Priority**: P2
**Preconditions**: Valid bearer token
**Steps**:
1. Request `GET /api/bookings/abc`, `DELETE /api/bookings/abc`, and open `/bookings/abc`.
**Expected Results**: UI reports booking not found. API must not return booking data; record the actual server response, since path IDs are converted to numbers without explicit route validation and may cause a 500.
**Business Rule**: Booking IDs are numeric; path parameters currently lack explicit validation.
**Suggested Layer**: API + E2E

### TC-303: Repeated cancellation returns not found
**Category**: Negative
**Priority**: P1
**Preconditions**: User owns a booking
**Steps**:
1. Cancel the booking successfully.
2. Submit the same cancellation request again.
**Expected Results**: First request returns 200; second returns 404 because the booking was deleted.
**Business Rule**: Cancellation is deletion, not a status update.
**Suggested Layer**: API

### TC-304: Stale detail page handles a booking deleted elsewhere
**Category**: Negative
**Priority**: P2
**Preconditions**: Same user has the booking detail open in two tabs
**Steps**:
1. Cancel the booking in Tab 1.
2. Confirm cancellation from the stale detail page in Tab 2.
**Expected Results**: Tab 2 shows an error toast with the server message, closes the dialog, and does not crash.
**Business Rule**: Cancellation errors are surfaced through the mutation error handler.
**Suggested Layer**: E2E

### TC-305: Missing create-booking fields are rejected
**Category**: Negative
**Priority**: P1
**Preconditions**: Valid bearer token
**Steps**:
1. Submit `POST /api/bookings` with an empty body.
**Expected Results**: Response is 400 with `success: false`, `error: "Validation failed"`, and validation details for event ID, customer name, email, phone, and quantity; no booking is created.
**Business Rule**: Required fields are enforced before the service runs.
**Suggested Layer**: API

### TC-306: Invalid quantity is rejected
**Category**: Negative
**Priority**: P0
**Preconditions**: Valid token and visible event
**Steps**:
1. Submit separate create requests with quantity 0, -1, 11, 1.5, nonnumeric text, and null.
**Expected Results**: Each invalid quantity returns 400 with a quantity validation detail; no booking is created.
**Business Rule**: Quantity must be an integer from 1 through 10.
**Suggested Layer**: API

### TC-307: Invalid customer fields are rejected
**Category**: Negative
**Priority**: P1
**Preconditions**: Valid bearer token and visible event
**Steps**:
1. Try a one-character or whitespace-only name, malformed email, phone shorter than 10 characters, and phone containing letters.
**Expected Results**: Each request returns 400 with a field-specific validation message and creates no booking.
**Business Rule**: Name requires at least two trimmed characters, email must be valid, phone must be at least 10 characters and use allowed characters.
**Suggested Layer**: API

### TC-308: Insufficient personal seats prevent booking
**Category**: Negative
**Priority**: P0
**Preconditions**: User-created event has 3 seats and user has no booking on it
**Steps**:
1. Book 2 tickets successfully.
2. Try booking 2 more tickets for that event.
**Expected Results**: Second request returns 400 with "Only 1 seat(s) available, but 2 requested"; no second booking is created.
**Business Rule**: Booking quantity cannot exceed per-user available seats.
**Suggested Layer**: API

### TC-309: Invalid or nonexistent event ID is rejected
**Category**: Negative
**Priority**: P1
**Preconditions**: Valid bearer token
**Steps**:
1. Submit a booking for a positive ID with no visible event.
2. Repeat with event ID 0 and nonnumeric event ID.
**Expected Results**: Unknown positive ID returns 404; zero or nonnumeric ID returns 400 validation failure; no booking is created.
**Business Rule**: Event ID must be a positive integer and visible to the user.
**Suggested Layer**: API

### TC-310: Booking list error offers retry
**Category**: Negative
**Priority**: P2
**Preconditions**: User is authenticated; test can intercept booking-list request
**Steps**:
1. Make the list request fail, open `/bookings`, then restore the request and select "Retry".
**Expected Results**: Error state says "Couldn't load bookings" with retry guidance; successful retry displays the list.
**Business Rule**: List query error state.
**Suggested Layer**: E2E

### TC-311: Clear-all request failure does not silently clear data
**Category**: Negative
**Priority**: P2
**Preconditions**: User owns bookings; clear-all request can be made to fail
**Steps**:
1. Fail `DELETE /api/bookings`, accept the browser confirmation, and select "Clear all bookings".
**Expected Results**: Existing bookings remain and the button returns to its normal label. Current code does not show an error toast and lets the rejected promise propagate; record this as a user-feedback defect.
**Business Rule**: Failed clear-all should preserve data and communicate failure.
**Suggested Layer**: E2E

### TC-312: Invalid pagination parameters do not expose unrelated records
**Category**: Negative
**Priority**: P2
**Preconditions**: Valid bearer token
**Steps**:
1. Request bookings using a negative page and using zero page/limit.
**Expected Results**: Zero values fall back to page 1 and limit 10. Record the negative page behavior; it should be rejected or safely normalized, not expose another user's bookings.
**Business Rule**: Pagination is user-scoped; query values are converted by the service.
**Suggested Layer**: API

---

## Edge Cases

### TC-400: Ninth booking does not prune an existing booking
**Category**: Edge Case
**Priority**: P1
**Preconditions**: User has exactly 8 bookings
**Steps**:
1. Create a ninth valid booking.
**Expected Results**: Total becomes 9 and all eight existing bookings remain.
**Business Rule**: Pruning begins when a new booking request starts with 9 or more existing bookings.
**Suggested Layer**: API

### TC-401: Quantity boundaries 1 and 10 are accepted
**Category**: Edge Case
**Priority**: P1
**Preconditions**: Event has at least 10 personal seats
**Steps**:
1. Create a booking for 1 ticket and another booking for 10 tickets.
**Expected Results**: Both requests succeed; totals are calculated correctly; cards use singular "ticket" for 1 and plural "tickets" for 10.
**Business Rule**: Quantity range is inclusive from 1 to 10.
**Suggested Layer**: API + E2E

### TC-402: Booking exactly remaining seats leaves zero
**Category**: Edge Case
**Priority**: P1
**Preconditions**: User-created event has 5 available seats for the user
**Steps**:
1. Book 5 tickets and read event availability.
2. Attempt to book one additional ticket.
**Expected Results**: First booking succeeds and availability becomes 0; second returns 400 with zero seats available.
**Business Rule**: Requested quantity must be no greater than remaining personal seats.
**Suggested Layer**: API

### TC-403: Refund boundary at quantity 2 is ineligible
**Category**: Edge Case
**Priority**: P1
**Preconditions**: User owns a booking for exactly 2 tickets
**Steps**:
1. Check refund eligibility on its detail page.
**Expected Results**: Result states that a 2-ticket group booking is not refundable.
**Business Rule**: Only quantity 1 is eligible.
**Suggested Layer**: Component

### TC-404: Clear-all with no bookings is safe and confirmation can be dismissed
**Category**: Edge Case
**Priority**: P1
**Preconditions**: User has one test state with no bookings and another with at least one booking
**Steps**:
1. Confirm clear-all with zero bookings.
2. With bookings present, dismiss the native clear-all confirmation instead of accepting it.
**Expected Results**: Empty clear-all succeeds with a deleted count of zero. Dismissing confirmation sends no DELETE request and preserves existing bookings.
**Business Rule**: Clear-all is safe for empty lists and requires confirmation.
**Suggested Layer**: API + E2E

### TC-405: Email normalization is reflected in stored booking data
**Category**: Edge Case
**Priority**: P2
**Preconditions**: Valid bearer token and visible event
**Steps**:
1. Create a booking using a mixed-case Gmail address with dots and a plus tag.
2. Retrieve the booking by ID.
**Expected Results**: Stored email reflects `normalizeEmail()` behavior, including Gmail-specific normalization; document the exact returned value and confirm whether it is intended.
**Business Rule**: Booking validator normalizes email input.
**Suggested Layer**: API

### TC-406: Phone validation counts characters, not digits
**Category**: Edge Case
**Priority**: P2
**Preconditions**: Valid bearer token and visible event
**Steps**:
1. Submit a phone value consisting of 10 allowed punctuation characters and no digits.
**Expected Results**: Current validator accepts the value because it checks string length and allowed characters, despite the documented "digits" requirement. Record as a validation defect.
**Business Rule**: Customer phone should contain at least 10 digits.
**Suggested Layer**: API / Unit

### TC-407: Numeric strings for event ID and quantity are coerced
**Category**: Edge Case
**Priority**: P2
**Preconditions**: Valid token and event with enough seats
**Steps**:
1. Create a booking with event ID and quantity supplied as integer strings.
**Expected Results**: Request succeeds; stored event ID and quantity are integers and total price uses the coerced quantity.
**Business Rule**: Validator applies integer coercion to these fields.
**Suggested Layer**: API

### TC-408: Unusual title characters and reference collision fallback are handled
**Category**: Edge Case
**Priority**: P2
**Preconditions**: Ability to create an event with a numeric, accented, or emoji first character; unit test can mock reference lookup
**Steps**:
1. Book events whose titles start with a digit, accented letter, and emoji.
2. In a unit test, force all 10 generated references to collide.
**Expected Results**: Record generated prefixes and verify they can be stored and looked up. Ordinary title prefixes use `title[0].toUpperCase()`. The forced-collision fallback may violate the six-character suffix format and can collide because it is not checked; any format violation or unique-key error is a defect.
**Business Rule**: Reference prefix and collision fallback implementation.
**Suggested Layer**: API + Unit

### TC-409: Reference lookup case behavior is explicit
**Category**: Edge Case
**Priority**: P2
**Preconditions**: User owns a booking with a known mixed-case reference
**Steps**:
1. Look up the reference using its original casing and then lowercase casing.
**Expected Results**: Original casing returns the booking. Record lowercase lookup behavior for the database collation; do not assume case sensitivity without verification.
**Business Rule**: Booking reference is unique and used as a lookup key.
**Suggested Layer**: API

### TC-410: Booking reference uniqueness is maintained
**Category**: Edge Case
**Priority**: P2
**Preconditions**: Valid token and test data allowing repeated booking creation
**Steps**:
1. Collect generated references across repeated bookings, allowing FIFO pruning.
2. Add a unit case that forces a reference collision.
**Expected Results**: Normal persisted references are unique and match one uppercase letter, a hyphen, and six uppercase alphanumeric characters. Include a case that forces the timestamp fallback and verify it does not collide; current code does not verify fallback uniqueness.
**Business Rule**: Unique booking reference generation with collision retry.
**Suggested Layer**: Unit + API

### TC-411: Concurrent booking requests respect the per-user limit
**Category**: Edge Case
**Priority**: P2
**Preconditions**: User has 8 bookings and has seats for concurrent requests
**Steps**:
1. Submit multiple valid booking requests concurrently.
2. Retrieve the user's booking list.
**Expected Results**: Verify the total remains at or below 9 and no unrelated booking is lost. Current count-then-insert logic is not atomic; record any excess bookings or conflicting prune failures as a race-condition defect.
**Business Rule**: Maximum 9 bookings per user.
**Suggested Layer**: API

### TC-412: Fractional and free event prices are represented consistently
**Category**: Edge Case
**Priority**: P2
**Preconditions**: User can book one event with a fractional price and one free event
**Steps**:
1. Book quantity 3 for a fractional-price event and quantity 2 for a zero-price event.
2. Compare stored totals with card and detail displays.
**Expected Results**: API totals equal exact price times quantity (rounded to the database's two decimal places); free booking total is zero. UI currently formats USD with zero fractional digits, so record any visible rounding mismatch.
**Business Rule**: Event price is nonnegative; total is price multiplied by quantity.
**Suggested Layer**: API + Component

### TC-413: A manually requested empty page does not masquerade as a full empty account
**Category**: Edge Case
**Priority**: P2
**Preconditions**: User owns 1–9 bookings
**Steps**:
1. Open `/bookings?page=2`.
**Expected Results**: Record current behavior. Since the page has no results and pagination is hidden for one page, it may show "No bookings yet" despite bookings existing; flag this as a UX defect if reproduced.
**Business Rule**: Pagination state should distinguish an empty page from an empty booking list.
**Suggested Layer**: E2E

### TC-414: Booking list filters and pagination return matching records
**Category**: Edge Case
**Priority**: P2
**Preconditions**: User has bookings for at least two events and enough records to span pages when using a smaller limit
**Steps**:
1. Request by `eventId`, by `status=confirmed`, by `status=cancelled`, and with `limit=2&page=2`.
**Expected Results**: Each filter limits returned data and `pagination.total` correctly; cancelled filter is empty because cancellation deletes rows; page 2 returns the next two newest matching records.
**Business Rule**: Booking list supports event/status filters and pagination.
**Suggested Layer**: API

---

## UI State

### TC-500: Booking cards show skeletons while loading
**Category**: UI State
**Priority**: P2
**Preconditions**: Authenticated user; booking-list response can be delayed
**Steps**:
1. Delay the booking-list response and open `/bookings`.
**Expected Results**: Five pulsing skeleton cards appear while loading and are replaced by results after the response.
**Business Rule**: Booking list loading state.
**Suggested Layer**: Component / E2E

### TC-501: Empty booking list offers event browsing
**Category**: UI State
**Priority**: P1
**Preconditions**: User owns no bookings
**Steps**:
1. Open `/bookings`.
**Expected Results**: "No bookings yet" and browse guidance appear with a "Browse Events" action to `/events`; "Clear all bookings" remains visible.
**Business Rule**: Empty state and always-rendered clear-all action.
**Suggested Layer**: E2E

### TC-502: Booking detail shows a loading indicator
**Category**: UI State
**Priority**: P2
**Preconditions**: User owns a booking; detail API response can be delayed
**Steps**:
1. Delay `GET /api/bookings/:id` and open the detail route.
**Expected Results**: A centered large spinner appears until the booking detail is loaded.
**Business Rule**: Detail query loading state.
**Suggested Layer**: Component / E2E

### TC-503: Cancel dialogs show accurate booking details
**Category**: UI State
**Priority**: P2
**Preconditions**: User owns a booking with reference R and quantity Q
**Steps**:
1. Open the cancel dialog from the detail page and from the booking card.
**Expected Results**: Both dialogs are titled "Cancel this booking?" and offer "Cancel" and "Yes, cancel it"; descriptions identify R and Q and warn that the action cannot be undone.
**Business Rule**: Confirmation is required before cancellation.
**Suggested Layer**: Component

### TC-504: Dismissing individual cancellation leaves the booking unchanged
**Category**: UI State
**Priority**: P1
**Preconditions**: User owns a booking
**Steps**:
1. Open the cancel dialog and close it using Cancel, Escape, overlay, and close control in separate runs.
**Expected Results**: Dialog closes, no DELETE request is sent, and the booking remains.
**Business Rule**: Cancellation requires explicit confirmation.
**Suggested Layer**: Component / E2E

### TC-505: In-flight cancellation prevents accidental dialog dismissal
**Category**: UI State
**Priority**: P2
**Preconditions**: Cancellation request can be delayed
**Steps**:
1. Confirm cancellation and attempt to close the dialog before the response completes.
**Expected Results**: Confirm button shows loading state; Cancel is disabled and modal close handling is disabled until request completion. Verify Escape does not throw: current Modal invokes its Escape handler even when `onClose` is undefined during loading, which may raise a client error.
**Business Rule**: ConfirmDialog loading behavior must prevent dismissal without triggering a runtime error.
**Suggested Layer**: Component

### TC-506: Clear-all button shows its pending state
**Category**: UI State
**Priority**: P2
**Preconditions**: User owns bookings and clear-all response can be delayed
**Steps**:
1. Accept the native confirmation and start clear-all.
**Expected Results**: Button reads "Clearing…" and is disabled until the request completes, then returns to "Clear all bookings".
**Business Rule**: Clear-all mutation pending state.
**Suggested Layer**: E2E

### TC-507: Refund section transitions through idle, checking, and result states
**Category**: UI State
**Priority**: P2
**Preconditions**: User is viewing a booking detail
**Steps**:
1. Observe the initial refund section, start the check, wait for the result, then reload.
**Expected Results**: Idle shows only the check button; checking shows spinner/message; result shows eligibility; reload resets to idle because state is client-only and not persisted.
**Business Rule**: Client-side refund state machine.
**Suggested Layer**: Component

### TC-508: Detail breadcrumb and header identify the booking
**Category**: UI State
**Priority**: P2
**Preconditions**: Booking has reference R and confirmed status
**Steps**:
1. Open the booking detail page.
**Expected Results**: Breadcrumb ends in R, header displays R and event title, and confirmed status has success styling.
**Business Rule**: Booking detail presentation.
**Suggested Layer**: E2E

### TC-509: Access-denied and not-found states differ only for 403
**Category**: UI State
**Priority**: P1
**Preconditions**: Booking detail request can be mocked with different HTTP statuses
**Steps**:
1. Load the detail route with responses 403, 404, and 500.
**Expected Results**: 403 shows "Access Denied" and authorization guidance; 404 and 500 show "Booking not found" and not-found guidance. Each state offers "View My Bookings".
**Business Rule**: UI selects the access-denied state only when error status is 403.
**Suggested Layer**: Component / E2E

### TC-510: Pagination is available for multiple pages but absent under normal limits
**Category**: UI State
**Priority**: P2
**Preconditions**: Mocked list can provide multiple pages; normal user has at most 9 bookings
**Steps**:
1. Load a mocked response with more than 10 bookings and navigate between pages.
2. Load normal data with no more than 9 bookings.
**Expected Results**: Multiple-page response shows working pagination and updates the `page` query parameter; normal data has no pagination because UI page size is 10 and the user limit is 9.
**Business Rule**: Pagination metadata drives the list control; normal limit prevents a second page.
**Suggested Layer**: Component / E2E

### TC-511: Bookings page does not show the documented sandbox banner
**Category**: UI State
**Priority**: P2
**Preconditions**: User has bookings near the account limit
**Steps**:
1. Open `/bookings`, then open `/events`.
**Expected Results**: `/bookings` has no sandbox banner, while `/events` may show its sandbox warning. This conflicts with the domain docs' statement that bookings has a conditional banner; confirm intended behavior.
**Business Rule**: Sandbox banner display.
**Suggested Layer**: E2E

### TC-512: Booking queries refresh after create and cancel
**Category**: UI State
**Priority**: P1
**Preconditions**: Authenticated user can create and cancel bookings
**Steps**:
1. Cancel a booking from the list and create a new booking.
**Expected Results**: Cancelled card disappears and the new booking appears at the top without manual reload; event data is refreshed after each mutation.
**Business Rule**: Booking mutations invalidate booking and event React Query caches.
**Suggested Layer**: E2E

### TC-513: Booking cards format quantity, date, and total
**Category**: UI State
**Priority**: P2
**Preconditions**: User owns bookings with quantity 1 and quantity greater than 1
**Steps**:
1. View the bookings list.
**Expected Results**: Quantity text uses "ticket" for 1 and "tickets" otherwise; dates use the en-IN short date format; totals use USD with zero fractional digits.
**Business Rule**: Booking card formatting and pluralization.
**Suggested Layer**: Component

### TC-514: Admin bookings view supports loading, empty, filtered, and error states
**Category**: UI State
**Priority**: P2
**Preconditions**: Admin booking API can be delayed, filtered, emptied, or failed
**Steps**:
1. Load `/admin/bookings` under each response condition and change the status filter.
**Expected Results**: Loading shows a spinner; empty/filter-miss shows "No bookings found"; errors show retry guidance; status filter resets to page 1; successful results display the corresponding rows.
**Business Rule**: Admin bookings query state and status filter behavior.
**Suggested Layer**: Component / E2E
