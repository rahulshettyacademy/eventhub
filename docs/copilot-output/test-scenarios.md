# Test Scenarios: Booking Management

Scope: booking creation, viewing, cancellation, clear-all, refund eligibility, booking limits,
seat availability, validation, authorization, and user-visible loading/error/empty states. Scenarios
are based on `eventhub-domain.md`, `.claude/references/eventhub-domain/business-rules.md`,
`.claude/references/eventhub-domain/user-flows.md`, the booking API reference, and the current
booking service, validator, and frontend pages.

## Happy Path (TC-001-099)

### TC-001: Create a single-ticket booking

**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is authenticated; a visible event has at least one available seat.
**Steps**:

1. Open the event detail page.
2. Enter valid customer name, email, and phone; keep quantity at 1.
3. Submit the booking.
   **Expected Results**: Booking confirmation displays a reference, customer, quantity 1, and total equal to the event price; the booking is available from the user's bookings list.
   **Business Rule**: Flow 3; `totalPrice = event.price x quantity`.
   **Suggested Layer**: E2E

### TC-002: Create a multi-ticket booking and verify its total

**Category**: Happy Path
**Priority**: P0
**Preconditions**: User is authenticated; event has at least 3 available seats.
**Steps**:

1. Set ticket quantity to 3 and enter valid customer details.
2. Submit the booking and open its detail page.
   **Expected Results**: Confirmation and detail show quantity 3 and total paid equal to three times the per-ticket price.
   **Business Rule**: Business Rules section 9.
   **Suggested Layer**: E2E

### TC-003: View the user's bookings list

**Category**: Happy Path
**Priority**: P0
**Preconditions**: User has at least one booking.
**Steps**:

1. Navigate to `/bookings`.
   **Expected Results**: Each owned booking appears with its event, reference, and confirmed status; no other user's booking is shown.
   **Business Rule**: Flow 4; user sandbox isolation.
   **Suggested Layer**: E2E

### TC-004: Open a booking detail page

**Category**: Happy Path
**Priority**: P0
**Preconditions**: User owns a booking.
**Steps**:

1. Open that booking from `/bookings` using its details link.
   **Expected Results**: Detail shows booking reference/status, event details, customer details, ticket count, price, total, refund section, and booking metadata.
   **Business Rule**: Flow 4; Booking data model.
   **Suggested Layer**: E2E

### TC-005: Cancel a booking after confirmation

**Category**: Happy Path
**Priority**: P0
**Preconditions**: User owns a confirmed booking.
**Steps**:

1. Open the booking detail page and select "Cancel Booking".
2. Confirm the cancellation.
   **Expected Results**: A success notification appears, the user returns to `/bookings`, and the cancelled booking is absent; its seats become available again according to the event's availability rules.
   **Business Rule**: Business Rules sections 1, 4, and 6.
   **Suggested Layer**: E2E

### TC-006: Clear all bookings

**Category**: Happy Path
**Priority**: P0
**Preconditions**: User owns multiple bookings.
**Steps**:

1. On `/bookings`, select "Clear all bookings".
2. Accept the confirmation dialog.
   **Expected Results**: All of the user's bookings are removed, and the empty-list state appears; another user's bookings remain unaffected.
   **Business Rule**: Business Rules sections 2 and 4.
   **Suggested Layer**: E2E / API

### TC-007: Check refund eligibility for one ticket

**Category**: Happy Path
**Priority**: P1
**Preconditions**: User owns a booking with quantity 1.
**Steps**:

1. Open its detail page and select "Check eligibility for refund?".
   **Expected Results**: A checking state appears, followed by the eligible result and the single-ticket refund message after approximately four seconds; no refund API request is made.
   **Business Rule**: Business Rules section 8; eligibility is client-side only.
   **Suggested Layer**: E2E / Component

### TC-008: Check refund eligibility for a group booking

**Category**: Happy Path
**Priority**: P1
**Preconditions**: User owns a booking with quantity greater than 1.
**Steps**:

1. Open its detail page and select "Check eligibility for refund?".
   **Expected Results**: After approximately four seconds, the result says the group booking is not eligible and displays the correct ticket quantity.
   **Business Rule**: Business Rules section 8.
   **Suggested Layer**: E2E / Component

### TC-009: Open an owned booking by reference through the API

**Category**: Happy Path
**Priority**: P2
**Preconditions**: User owns a booking and knows its reference.
**Steps**:

1. Send `GET /api/bookings/ref/:ref` with that user's bearer token.
   **Expected Results**: API returns success and the matching booking.
   **Business Rule**: Booking API reference lookup.
   **Suggested Layer**: API

### TC-010: Navigate from booking confirmation to the bookings list

**Category**: Happy Path
**Priority**: P1
**Preconditions**: User has just created a booking and sees its confirmation.
**Steps**:

1. Select "View My Bookings".
   **Expected Results**: Browser navigates to `/bookings` and the newly created booking is present.
   **Business Rule**: Flow 3.
   **Suggested Layer**: E2E

## Business Rules (TC-100-199)

### TC-100: Booking reference uses the event title's first character

**Category**: Business Rule
**Priority**: P0
**Preconditions**: A booking can be created for an event with a known title.
**Steps**:

1. Create the booking and inspect its reference.
   **Expected Results**: Reference starts with the uppercase first character of the event title, followed by a hyphen and six alphanumeric characters.
   **Business Rule**: Business Rules section 7.
   **Suggested Layer**: API / Unit

### TC-101: Booking reference is unique

**Category**: Business Rule
**Priority**: P1
**Preconditions**: User can create multiple bookings, including for events with the same initial.
**Steps**:

1. Create multiple bookings and collect their references.
   **Expected Results**: No two bookings share a reference; generated references follow the documented format.
   **Business Rule**: Business Rules section 7; unique reference collision retry.
   **Suggested Layer**: Unit

### TC-102: Booking quantity reduces available seats

**Category**: Business Rule
**Priority**: P0
**Preconditions**: User can observe an event with a known available-seat count.
**Steps**:

1. Record the available seats.
2. Book N seats and reload the event.
   **Expected Results**: The user's available seat count is reduced by N.
   **Business Rule**: Business Rules sections 1 and 6.
   **Suggested Layer**: E2E / API

### TC-103: Cancelling a booking restores its seats

**Category**: Business Rule
**Priority**: P0
**Preconditions**: User has a booking whose quantity affects the event's available seats.
**Steps**:

1. Record the available seats, cancel the booking, then reload the event.
   **Expected Results**: The cancelled booking no longer contributes to the user's booked quantity, and its seats are available again.
   **Business Rule**: Business Rules sections 4 and 6.
   **Suggested Layer**: E2E / API

### TC-104: Booking limit keeps no more than nine bookings

**Category**: Business Rule
**Priority**: P0
**Preconditions**: User has nine bookings.
**Steps**:

1. Create another valid booking.
2. Fetch the user's bookings.
   **Expected Results**: The new booking exists, the oldest booking is pruned, and the user has nine bookings total.
   **Business Rule**: Business Rules section 4; `MAX_USER_BOOKINGS` is 9.
   **Suggested Layer**: API

### TC-105: FIFO pruning prefers an older booking for a different event

**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has nine bookings, including an oldest booking for a different event from the new booking.
**Steps**:

1. Create a valid booking for another event.
2. Inspect the bookings list.
   **Expected Results**: The oldest booking for a different event is removed first, and the new booking is retained.
   **Business Rule**: Business Rules section 4; booking service FIFO selection.
   **Suggested Layer**: API

### TC-106: Same-event FIFO fallback preserves the seat-count rule

**Category**: Business Rule
**Priority**: P2
**Preconditions**: User has nine bookings and all are for the event being booked again; event has enough seats.
**Steps**:

1. Record the event's available-seat count.
2. Create another booking for that same event.
3. Inspect the remaining bookings and available-seat count.
   **Expected Results**: The oldest same-event booking is removed, the new one is stored, the total remains nine, and the resulting seat count reflects the new booking rather than silently restoring the pruned booking's quantity.
   **Business Rule**: Business Rules section 4; `sameEventFallback` in `bookingService.createBooking`.
   **Suggested Layer**: API

### TC-107: Booking list filters are scoped to the authenticated user

**Category**: Business Rule
**Priority**: P1
**Preconditions**: User has bookings with different event IDs and statuses where applicable.
**Steps**:

1. Request `/api/bookings` with `eventId` and/or `status` filters.
   **Expected Results**: Returned records match the requested filters and belong only to the authenticated user; pagination metadata reflects the filtered result.
   **Business Rule**: Booking API list filters; user sandbox isolation.
   **Suggested Layer**: API

### TC-108: Clear-all reports the number of removed bookings

**Category**: Business Rule
**Priority**: P2
**Preconditions**: User has a known number of bookings.
**Steps**:

1. Send `DELETE /api/bookings` using the user's token.
   **Expected Results**: Only that user's records are deleted, and the response reports the number deleted.
   **Business Rule**: Booking API; `clearAllBookings` returns the deleted count.
   **Suggested Layer**: API

## Security (TC-200-299)

### TC-200: User cannot view another user's booking by ID

**Category**: Security
**Priority**: P0
**Preconditions**: User A owns a booking; User B has a separate account.
**Steps**:

1. As User B, request the booking detail endpoint using User A's booking ID and open its detail URL.
   **Expected Results**: API returns 403; UI shows "Access Denied" and does not reveal booking details.
   **Business Rule**: Business Rules section 2; booking ownership check.
   **Suggested Layer**: API / E2E

### TC-201: User cannot look up another user's booking by reference

**Category**: Security
**Priority**: P1
**Preconditions**: User B knows User A's booking reference.
**Steps**:

1. Request `GET /api/bookings/ref/:ref` using User B's token.
   **Expected Results**: API returns 403 and does not return the booking data.
   **Business Rule**: Business Rules section 2; booking reference ownership check.
   **Suggested Layer**: API

### TC-202: User cannot cancel another user's booking

**Category**: Security
**Priority**: P0
**Preconditions**: User A owns a booking; User B has its ID.
**Steps**:

1. Send `DELETE /api/bookings/:id` with User B's token.
2. Re-fetch the booking as User A.
   **Expected Results**: User B receives 403; the booking and its seat allocation remain unchanged for User A.
   **Business Rule**: Business Rules section 2; cancellation is owner-scoped.
   **Suggested Layer**: API

### TC-203: Unauthenticated booking requests are rejected

**Category**: Security
**Priority**: P0
**Preconditions**: No valid authentication token is present.
**Steps**:

1. Call each booking endpoint without a bearer token.
2. Open `/bookings` in a browser with authentication cleared.
   **Expected Results**: API requests return 401; the protected page redirects to login or otherwise blocks access without showing booking data.
   **Business Rule**: Booking API requires bearer authentication; missing-token error.
   **Suggested Layer**: API / E2E

### TC-204: Booking a private event owned by another user is denied

**Category**: Security
**Priority**: P1
**Preconditions**: User B owns a dynamic event; User A does not own it.
**Steps**:

1. As User A, submit a booking request with User B's dynamic event ID.
   **Expected Results**: API returns 404 and does not create a booking for the inaccessible event.
   **Business Rule**: Event sandbox isolation; booking creation resolves static or requester-owned events.
   **Suggested Layer**: API

## Negative / Error (TC-300-399)

### TC-300: Booking form rejects a name shorter than two characters

**Category**: Negative
**Priority**: P1
**Preconditions**: User is on an available event's booking form.
**Steps**:

1. Enter a one-character name and otherwise valid values.
2. Submit the form.
   **Expected Results**: Name validation is shown and no booking request is sent; the API also rejects a one-character name with 400.
   **Business Rule**: Booking model minimum name length; `bookingValidator`.
   **Suggested Layer**: E2E / API

### TC-301: Booking form and API reject an invalid email

**Category**: Negative
**Priority**: P1
**Preconditions**: User is on the booking form or has a valid API token.
**Steps**:

1. Submit an invalid email such as `not-an-email` through the form and API.
   **Expected Results**: Form blocks submission with an email error; API returns 400 validation details and creates no booking.
   **Business Rule**: `customerEmail` must be a valid email address.
   **Suggested Layer**: E2E / API

### TC-302: API rejects phone values with fewer than ten digits

**Category**: Negative
**Priority**: P1
**Preconditions**: User has a valid token and event ID.
**Steps**:

1. Submit a phone value containing fewer than ten digits, including a value padded with allowed punctuation to at least ten characters.
   **Expected Results**: API returns 400 because the phone number does not contain ten digits; no booking is created.
   **Business Rule**: Customer phone minimum is ten digits; validator must enforce digit count, not only string length.
   **Suggested Layer**: API

### TC-303: API rejects missing or malformed booking fields

**Category**: Negative
**Priority**: P1
**Preconditions**: User has a valid token.
**Steps**:

1. Submit a booking request omitting `eventId`, customer details, or `quantity`.
2. Repeat with a non-positive/non-integer event ID or fractional quantity.
   **Expected Results**: Each request returns 400 with field-level validation details and creates no booking.
   **Business Rule**: `validateCreateBooking`; required positive integer event ID and integer quantity 1-10.
   **Suggested Layer**: API

### TC-304: API rejects quantity outside the allowed range

**Category**: Negative
**Priority**: P1
**Preconditions**: User has a valid token; event has sufficient seats for the attempted quantity.
**Steps**:

1. Submit quantity 0, then quantity 11.
   **Expected Results**: Both requests return 400 with the quantity range validation error; no booking is created.
   **Business Rule**: Booking quantity must be an integer from 1 through 10.
   **Suggested Layer**: API

### TC-305: Booking more seats than available is rejected

**Category**: Negative
**Priority**: P0
**Preconditions**: Event has fewer available seats than the requested quantity.
**Steps**:

1. Submit the booking directly to the API with the excessive quantity.
   **Expected Results**: API returns 400 with the available/requested seat counts; no booking is created and the seat count is unchanged.
   **Business Rule**: Insufficient seats error; Business Rules section 6.
   **Suggested Layer**: API

### TC-306: Non-existent event cannot be booked

**Category**: Negative
**Priority**: P1
**Preconditions**: User has a valid token.
**Steps**:

1. Submit an otherwise valid booking request with a non-existent event ID.
   **Expected Results**: API returns 404 and no booking is created.
   **Business Rule**: Booking creation requires an existing visible event.
   **Suggested Layer**: API

### TC-307: Sold-out event cannot be booked

**Category**: Negative
**Priority**: P0
**Preconditions**: Event has zero available seats.
**Steps**:

1. Open the event detail page and inspect the booking control.
2. Attempt a direct API booking for one ticket.
   **Expected Results**: UI indicates sold out and disables submission; API returns 400 insufficient seats.
   **Business Rule**: Business Rules section 6; sold-out event behavior.
   **Suggested Layer**: E2E / API

### TC-308: Cancelling the same booking twice is handled safely

**Category**: Negative
**Priority**: P2
**Preconditions**: User owns one booking.
**Steps**:

1. Cancel the booking successfully.
2. Repeat the cancellation request with the same ID.
   **Expected Results**: First request succeeds; second returns 404 without changing unrelated bookings or crashing the UI.
   **Business Rule**: Cancellation only acts on an existing owned booking.
   **Suggested Layer**: API

### TC-309: Failed booking at the nine-booking limit does not lose an existing booking

**Category**: Negative
**Priority**: P0
**Preconditions**: User has nine bookings and the attempted new booking is invalid, references a missing event, or requests more seats than available.
**Steps**:

1. Record all nine booking IDs and attempt the invalid tenth booking.
2. Fetch the user's bookings again.
   **Expected Results**: The request fails, and all nine pre-existing bookings remain unchanged; no booking should be pruned unless the new booking succeeds.
   **Business Rule**: Failed booking attempts must not mutate existing bookings; atomic booking-limit handling.
   **Suggested Layer**: API

## Edge Cases (TC-400-499)

### TC-400: Book exactly the last available seat

**Category**: Edge Case
**Priority**: P1
**Preconditions**: Event has exactly one available seat.
**Steps**:

1. Book one ticket.
2. Reload the event detail page.
   **Expected Results**: Booking succeeds and the event shows zero available seats / sold out.
   **Business Rule**: Booking quantity cannot exceed availability.
   **Suggested Layer**: E2E / API

### TC-401: Book the maximum quantity of ten tickets

**Category**: Edge Case
**Priority**: P1
**Preconditions**: Event has at least ten available seats.
**Steps**:

1. Increase the quantity to ten and submit valid customer details.
   **Expected Results**: Booking succeeds with quantity ten; increment is disabled at the maximum, and the total is ten times the ticket price.
   **Business Rule**: Booking quantity range is 1-10.
   **Suggested Layer**: E2E

### TC-402: Quantity control cannot exceed available seats

**Category**: Edge Case
**Priority**: P1
**Preconditions**: Event has between one and nine available seats.
**Steps**:

1. Repeatedly activate the increment control.
   **Expected Results**: Quantity stops at the lower of ten or available seats; increment is disabled at that limit.
   **Business Rule**: Booking form maximum is `min(10, availableSeats)`.
   **Suggested Layer**: Component / E2E

### TC-403: Booking the tenth valid booking retains the new booking and prunes only one old booking

**Category**: Edge Case
**Priority**: P1
**Preconditions**: User has nine bookings; new request is valid and has enough seats.
**Steps**:

1. Record booking IDs and create the tenth booking.
2. Fetch the bookings list.
   **Expected Results**: Exactly one oldest booking is removed, the new booking appears, and the total stays at nine.
   **Business Rule**: Business Rules section 4; FIFO pruning.
   **Suggested Layer**: API

### TC-404: Reference generation handles a title beginning with a non-letter

**Category**: Edge Case
**Priority**: P2
**Preconditions**: A bookable event title begins with a digit or symbol.
**Steps**:

1. Create a booking for that event.
   **Expected Results**: The reference prefix uses the event title's first character uppercased and remains in the documented reference format.
   **Business Rule**: `randomRef` derives the prefix from the title's first character.
   **Suggested Layer**: Unit

### TC-405: Reference collision retry terminates

**Category**: Edge Case
**Priority**: P3
**Preconditions**: Unit test can mock reference lookup to return collisions for the retry attempts.
**Steps**:

1. Invoke reference generation with repeated collisions.
   **Expected Results**: Generation stops after the configured retry limit, returns a prefixed fallback reference, and does not loop indefinitely.
   **Business Rule**: `generateUniqueRef` retries up to ten times before fallback.
   **Suggested Layer**: Unit

### TC-406: Pagination metadata is consistent with the booking limit

**Category**: Edge Case
**Priority**: P2
**Preconditions**: User has between zero and nine bookings; API default limit is ten.
**Steps**:

1. Fetch `/api/bookings` at the default page and limit.
2. Inspect the response metadata and bookings page controls.
   **Expected Results**: `total`, `page`, `limit`, and `totalPages` are consistent; at the nine-booking cap, the list fits on one page and does not expose a misleading second page.
   **Business Rule**: Business Rules section 4; service defaults to a page limit of ten and prunes at nine bookings.
   **Suggested Layer**: API / E2E

## UI State (TC-500-599)

### TC-500: Bookings list shows loading placeholders while data loads

**Category**: UI State
**Priority**: P2
**Preconditions**: Booking-list request is delayed in a controlled test.
**Steps**:

1. Navigate to `/bookings` while the request is pending.
   **Expected Results**: Loading placeholders appear and are replaced by the loaded list or empty state when the request completes.
   **Business Rule**: Bookings page loading state.
   **Suggested Layer**: Component / E2E

### TC-501: Empty bookings list offers event browsing

**Category**: UI State
**Priority**: P1
**Preconditions**: User has no bookings.
**Steps**:

1. Navigate to `/bookings`.
   **Expected Results**: "No bookings yet" is displayed with a link/button to browse events; no stale booking cards appear.
   **Business Rule**: Flow 4; empty-list behavior.
   **Suggested Layer**: E2E

### TC-502: Bookings load failure offers a working retry

**Category**: UI State
**Priority**: P1
**Preconditions**: First bookings-list request fails; a subsequent request can succeed.
**Steps**:

1. Navigate to `/bookings` and observe the failed request state.
2. Select "Retry".
   **Expected Results**: Error state explains that bookings could not be loaded; retry refetches and displays the successful result.
   **Business Rule**: Bookings page error and retry state.
   **Suggested Layer**: Component / E2E

### TC-503: Missing booking detail shows a recoverable not-found state

**Category**: UI State
**Priority**: P1
**Preconditions**: User is authenticated and requests a non-existent or cancelled booking ID.
**Steps**:

1. Open the booking detail URL directly.
   **Expected Results**: "Booking not found" appears with a link to return to "My Bookings"; page does not crash or display another booking.
   **Business Rule**: Booking detail not-found state; API returns 404.
   **Suggested Layer**: E2E

### TC-504: Cancelling the confirmation dialog leaves the booking intact

**Category**: UI State
**Priority**: P1
**Preconditions**: User owns a confirmed booking.
**Steps**:

1. Select "Cancel Booking" to open the confirmation dialog.
2. Close the dialog without confirming.
   **Expected Results**: Dialog closes; no cancellation request is sent; booking remains confirmed and visible.
   **Business Rule**: Cancellation requires explicit confirmation.
   **Suggested Layer**: E2E / Component

### TC-505: Declining the clear-all confirmation preserves all bookings

**Category**: UI State
**Priority**: P1
**Preconditions**: User has one or more bookings.
**Steps**:

1. Select "Clear all bookings".
2. Dismiss the browser confirmation dialog.
   **Expected Results**: No delete request is sent, and every booking remains visible.
   **Business Rule**: Clear-all action requires confirmation.
   **Suggested Layer**: E2E

### TC-506: Booking detail loading and refund states resolve correctly

**Category**: UI State
**Priority**: P2
**Preconditions**: Booking detail data can be delayed; user owns a booking.
**Steps**:

1. Open the detail URL while the booking request is pending.
2. After it loads, start a refund eligibility check.
   **Expected Results**: Detail loading indicator is replaced by booking content; refund spinner is replaced by exactly one eligibility result after the delay.
   **Business Rule**: Booking detail loading state; Business Rules section 8.
   **Suggested Layer**: Component / E2E
