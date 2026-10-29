# Booking Management Test Scenarios

These scenarios cover the booking list, booking details, cancellation, clear-all, refund eligibility, ownership boundaries, and booking-management UI states.

**Shared-account safety:** All scenarios that create or delete records should use isolated test users/data and clean up only records they created. TC-103 specifically requires a disposable account because clear-all deletes every booking owned by that account; never run it against the shared QA account.

## Happy Path

### TC-001: View the current user's booking list
**Category**: Happy Path
**Priority**: P1
**Preconditions**: The authenticated test user owns at least one confirmed booking.
**Steps**:
1. Sign in as the booking owner.
2. Open **My Bookings** from the navigation.
3. Locate the card by its booking reference.
**Expected Results**: The page shows the booking's reference, confirmed status, event title, booking ID, date, ticket quantity, city, and total price; **View Details** and **Cancel Booking** are available.
**Business Rule**: The booking list shows bookings belonging to the authenticated user; confirmed bookings can be viewed and cancelled.
**Suggested Layer**: E2E

### TC-002: Open booking details from its list card
**Category**: Happy Path
**Priority**: P1
**Preconditions**: The user owns a confirmed booking with event and customer details.
**Steps**:
1. Sign in and open **My Bookings**.
2. Find the card by booking reference and select **View Details**.
**Expected Results**: The detail URL identifies the booking. The page shows the matching reference and event title, plus Event Details, Customer Details, Payment Summary, Refund, and Booking Information sections with values matching the booking.
**Business Rule**: A booking owner can view complete booking and associated event details.
**Suggested Layer**: E2E

### TC-003: Cancel a booking from its list card
**Category**: Happy Path
**Priority**: P1
**Preconditions**: The user owns two confirmed bookings; test data is isolated from other users.
**Steps**:
1. Open **My Bookings** and locate both cards by their references.
2. Select **Cancel Booking** on one card.
3. Verify the confirmation dialog identifies that booking and its ticket quantity.
4. Confirm cancellation.
**Expected Results**: A success toast appears, the dialog closes, and the user remains on the list. The cancelled card disappears while the other booking remains.
**Business Rule**: Cancelling one booking deletes only that booking and preserves other bookings.
**Suggested Layer**: E2E

### TC-004: Cancel a booking from its detail page
**Category**: Happy Path
**Priority**: P1
**Preconditions**: The user owns a confirmed booking.
**Steps**:
1. Open the booking's detail page.
2. Select **Cancel Booking** and review the confirmation dialog.
3. Confirm cancellation.
**Expected Results**: A success toast appears, the user is redirected to **My Bookings**, and the cancelled booking no longer appears in the list or can be retrieved as an existing booking.
**Business Rule**: A confirmed booking can be cancelled by its owner; successful cancellation deletes the booking.
**Suggested Layer**: E2E

### TC-005: Check refund eligibility for a single-ticket booking
**Category**: Happy Path
**Priority**: P1
**Preconditions**: The user owns a booking with quantity 1.
**Steps**:
1. Open the booking detail page.
2. Select **Check eligibility for refund?**
3. Observe the checking state until the result appears.
**Expected Results**: The checking spinner appears for approximately four seconds, then the page reports **Eligible for refund** and states that single-ticket bookings qualify for a full refund.
**Business Rule**: Refund eligibility is computed client-side; quantity 1 is eligible.
**Suggested Layer**: Component

### TC-006: Check refund eligibility for a group booking
**Category**: Happy Path
**Priority**: P1
**Preconditions**: The user owns a booking with quantity greater than 1.
**Steps**:
1. Open the booking detail page.
2. Select **Check eligibility for refund?**
3. Wait for the checking state to finish.
**Expected Results**: The result reports **Not eligible for refund** and identifies the booking as a non-refundable group booking with the correct ticket count.
**Business Rule**: Refund eligibility is computed client-side; bookings with quantity greater than 1 are not eligible.
**Suggested Layer**: Component

### TC-007: Return from booking details to the booking list
**Category**: Happy Path
**Priority**: P2
**Preconditions**: The user can access an owned booking's detail page.
**Steps**:
1. Open the booking details.
2. Select **My Bookings** in the breadcrumb or **Back to My Bookings**.
**Expected Results**: The browser returns to `/bookings` and the user's booking list is displayed.
**Business Rule**: Booking details provide navigation back to the user's booking list.
**Suggested Layer**: E2E

## Business Rules

### TC-100: List bookings newest first
**Category**: Business Rule
**Priority**: P1
**Preconditions**: The test user owns two bookings created at distinguishable times.
**Steps**:
1. Open **My Bookings** after both bookings exist.
2. Compare the displayed order using their references or IDs.
**Expected Results**: The most recently created booking appears before the older booking.
**Business Rule**: The booking repository orders the user's bookings by creation time descending.
**Suggested Layer**: E2E

### TC-101: Show the confirmed status and booking metadata
**Category**: Business Rule
**Priority**: P1
**Preconditions**: The user owns a confirmed booking.
**Steps**:
1. Open the booking card.
2. Open its details.
3. Compare displayed status, booking ID, reference, quantity, customer data, and event data with the persisted booking.
**Expected Results**: The booking is shown as confirmed, and both list and detail views display values belonging to the same booking.
**Business Rule**: Newly created bookings have status `confirmed`; list and detail data must identify the same booking.
**Suggested Layer**: E2E

### TC-102: Calculate and display total paid for the booking quantity
**Category**: Business Rule
**Priority**: P1
**Preconditions**: The user owns a booking for an event with a known per-ticket price and quantity greater than 1.
**Steps**:
1. Open the booking detail page.
2. Compare **Price per ticket**, **Tickets**, and **Total Paid**.
**Expected Results**: Total Paid equals the event's per-ticket price multiplied by the booking quantity and is formatted in USD without decimal places.
**Business Rule**: `totalPrice = event.price × quantity`.
**Suggested Layer**: Component

### TC-103: Clear all bookings after explicit confirmation
**Category**: Business Rule
**Priority**: P1
**Preconditions**: Use a dedicated disposable test account with at least two bookings and no unrelated data. Never use the shared QA account for this destructive scenario.
**Steps**:
1. Sign in as the disposable account and open **My Bookings**.
2. Select **Clear all bookings**.
3. Verify the native confirmation message.
4. Accept the confirmation.
**Expected Results**: The account's booking list becomes empty, the empty state appears, and the account's booking API response contains no records.
**Business Rule**: Clear-all deletes every booking owned by the current user after confirmation.
**Suggested Layer**: E2E

### TC-104: Preserve other users' bookings when one account clears all
**Category**: Business Rule
**Priority**: P1
**Preconditions**: Two dedicated test users each own at least one booking; User B has no unrelated bookings because the scenario clears all of User B's bookings.
**Steps**:
1. Sign in as User A and record User A's booking reference.
2. Sign in as User B and confirm the native **Clear all bookings** dialog.
3. Sign back in as User A and inspect the list or retrieve the booking through the authenticated API.
**Expected Results**: User B's bookings are cleared; User A's booking remains unchanged and accessible.
**Business Rule**: Clear-all deletes records scoped to the authenticated user's ID only.
**Suggested Layer**: API

### TC-105: Enforce the nine-booking per-user limit with FIFO pruning
**Category**: Business Rule
**Priority**: P1
**Preconditions**: A dedicated test account has nine identifiable bookings with a known oldest booking. The tenth booking targets a valid event not represented by any of those nine bookings and has enough seats.
**Steps**:
1. Record the existing nine booking references in creation order.
2. Create a tenth booking for the test account.
3. Reload **My Bookings** and compare the list with the recorded references.
**Expected Results**: The account never has more than nine bookings; the oldest booking is removed, and the newly created booking is present.
**Business Rule**: The maximum is nine bookings per user; when the limit is reached the oldest booking is automatically deleted.
**Suggested Layer**: API

### TC-106: Restore dynamic-event seat availability after cancellation
**Category**: Business Rule
**Priority**: P1
**Preconditions**: The user owns a booking for a user-created event, and the event's personal available-seat count can be queried.
**Steps**:
1. Record the event's available seats as displayed to the owner.
2. Cancel the booking.
3. Reload the event details or query the event using the same user.
**Expected Results**: The booking is gone and the user's available-seat count increases by the cancelled booking quantity.
**Business Rule**: Dynamic event seats are computed per user from total seats minus that user's booked quantity; deleting the booking restores those seats.
**Suggested Layer**: API

## Security

### TC-200: Require authentication to view the booking list
**Category**: Security
**Priority**: P1
**Preconditions**: The browser has no valid authentication token.
**Steps**:
1. Navigate directly to `/bookings`.
2. Observe the final URL and rendered page.
**Expected Results**: The unauthenticated visitor is redirected to `/login`; booking data is not displayed.
**Business Rule**: Booking routes require authentication; the frontend AuthGuard protects non-public pages.
**Suggested Layer**: E2E

### TC-201: Do not expose another user's bookings in the list
**Category**: Security
**Priority**: P1
**Preconditions**: User A and User B have separate accounts; User A owns a booking with a known reference.
**Steps**:
1. Sign in as User B.
2. Open `/bookings`.
3. Search the returned list for User A's booking reference and ID.
**Expected Results**: User A's booking is absent from User B's list and API response.
**Business Rule**: Booking lists are scoped to the authenticated user's ID.
**Suggested Layer**: API

### TC-202: Deny cross-user access to a booking detail page
**Category**: Security
**Priority**: P0
**Preconditions**: User A owns an existing booking; User B is authenticated separately.
**Steps**:
1. Capture User A's booking ID.
2. Sign in as User B.
3. Navigate directly to `/bookings/<User A booking ID>`.
**Expected Results**: The page displays **Access Denied** and does not disclose booking or customer details. The authenticated detail API request is forbidden.
**Business Rule**: Viewing another user's booking is forbidden and is shown as **Access Denied** in the UI.
**Suggested Layer**: API

### TC-203: Prevent one user from cancelling another user's booking
**Category**: Security
**Priority**: P0
**Preconditions**: User A owns a booking; User B has a valid independent token.
**Steps**:
1. As User B, send a cancellation request for User A's booking ID.
2. As User A, retrieve the booking and its event-seat state.
**Expected Results**: User B cannot cancel the booking; the record remains owned by User A and its event-seat state is unchanged. The endpoint returns a non-success response without disclosing or deleting the record.
**Business Rule**: A user may cancel only their own bookings; repository lookup and deletion are ownership-scoped.
**Suggested Layer**: API

### TC-204: Do not expose booking data through a guessed or unknown ID
**Category**: Security
**Priority**: P2
**Preconditions**: The user is authenticated; the requested booking ID does not exist.
**Steps**:
1. Navigate to `/bookings/<unknown numeric ID>`.
2. Inspect the page and response.
**Expected Results**: The page shows a not-found state and exposes no booking data.
**Business Rule**: A missing booking is not retrievable by ID.
**Suggested Layer**: API

## Negative and Error Scenarios

### TC-300: Handle a nonexistent booking ID on the detail page
**Category**: Negative
**Priority**: P1
**Preconditions**: The user is authenticated; the numeric booking ID does not exist.
**Steps**:
1. Navigate directly to the nonexistent booking's detail URL.
2. Select **View My Bookings** from the error state.
**Expected Results**: The page displays **Booking not found** with a useful explanation and a working link back to `/bookings`.
**Business Rule**: Missing bookings produce a not-found response, not a success-shaped empty detail page.
**Suggested Layer**: E2E

### TC-301: Handle a previously cancelled booking URL
**Category**: Negative
**Priority**: P2
**Preconditions**: The user owned a booking that has already been cancelled or deleted.
**Steps**:
1. Open the old booking detail URL.
2. Inspect the page state.
**Expected Results**: The UI reports **Booking not found** and does not show stale booking details or offer cancellation.
**Business Rule**: Cancellation deletes the booking; deleted records are no longer available by ID.
**Suggested Layer**: E2E

### TC-302: Dismiss an individual cancellation confirmation
**Category**: Negative
**Priority**: P1
**Preconditions**: The user owns a confirmed booking and can query its existence.
**Steps**:
1. Open the booking list or detail page.
2. Select **Cancel Booking**.
3. Select **Cancel** in the confirmation dialog or dismiss it using a supported dialog close action.
**Expected Results**: The dialog closes, no cancellation request is sent, the booking remains visible and confirmed, and its seat availability is unchanged.
**Business Rule**: Cancellation requires explicit confirmation; dismissing confirmation must not mutate booking state.
**Suggested Layer**: Component

### TC-303: Dismiss the native clear-all confirmation
**Category**: Negative
**Priority**: P1
**Preconditions**: Use a dedicated disposable test account with at least one booking.
**Steps**:
1. Open **My Bookings** and record the booking.
2. Select **Clear all bookings**.
3. Dismiss the native confirmation.
**Expected Results**: The booking list remains unchanged, the booking is still retrievable, and no clear-all request is sent.
**Business Rule**: Clear-all runs only after the user accepts the native confirmation.
**Suggested Layer**: E2E

### TC-304: Surface an individual-cancellation API failure
**Category**: Negative
**Priority**: P2
**Preconditions**: The user owns a booking; the cancellation endpoint can be made to return a controlled server error without changing stored data.
**Steps**:
1. Open the booking and request cancellation.
2. Confirm cancellation while the test intercepts the endpoint with a failure response.
3. Inspect the toast, dialog, and booking state.
**Expected Results**: An error toast is shown, the confirmation dialog closes, and the booking remains visible and unchanged so the user can retry.
**Business Rule**: A failed cancellation must be surfaced and must not be represented as a successful deletion.
**Suggested Layer**: Component

### TC-305: Preserve existing bookings when a new booking is rejected at the account limit
**Category**: Negative
**Priority**: P1
**Preconditions**: A dedicated test account has nine identifiable bookings. The attempted tenth booking targets a valid event for which the user has insufficient available seats.
**Steps**:
1. Record all nine booking IDs and references.
2. Attempt to create the tenth booking with a quantity greater than the available seats.
3. Verify the request is rejected, then reload the booking list and retrieve the original records.
**Expected Results**: The booking attempt fails with an insufficient-seats response, and all nine pre-existing bookings remain unchanged; a rejected booking attempt does not prune valid user data.
**Business Rule**: FIFO pruning is a limit policy for successful booking creation; a rejected operation must not delete an existing booking.
**Suggested Layer**: API

## Edge Cases

### TC-400: Show the empty state when the account has no bookings
**Category**: Edge Case
**Priority**: P1
**Preconditions**: The authenticated test account has zero bookings.
**Steps**:
1. Open `/bookings`.
2. Inspect the empty-state content and action.
**Expected Results**: **No bookings yet** appears with explanatory text and a working **Browse Events** action.
**Business Rule**: An empty booking collection is a valid state and must not be treated as a load failure.
**Suggested Layer**: E2E

### TC-401: Display exactly one booking without duplicate or missing cards
**Category**: Edge Case
**Priority**: P2
**Preconditions**: The isolated user owns exactly one booking.
**Steps**:
1. Open **My Bookings**.
2. Count booking cards and locate the card by its reference.
**Expected Results**: Exactly one card is shown for that booking, with singular ticket wording when its quantity is one.
**Business Rule**: List rendering reflects the actual number of bookings and uses singular wording for quantity 1.
**Suggested Layer**: Component

### TC-402: Display the maximum nine bookings without an extra page
**Category**: Edge Case
**Priority**: P2
**Preconditions**: The isolated user owns the maximum of nine bookings.
**Steps**:
1. Open **My Bookings**.
2. Count the cards and inspect pagination controls.
**Expected Results**: Nine bookings are available in the list; no second page is needed because the page limit is ten and the user limit is nine.
**Business Rule**: A user can hold no more than nine bookings; the bookings page requests ten records per page.
**Suggested Layer**: Component

### TC-403: Cancel the only booking and return to the empty state
**Category**: Edge Case
**Priority**: P1
**Preconditions**: The user owns exactly one confirmed booking.
**Steps**:
1. Cancel the booking and confirm the dialog.
2. Wait for the booking list to finish loading.
**Expected Results**: The cancelled card disappears and **No bookings yet** appears without a stale card or error state.
**Business Rule**: Deleting the user's final booking leaves a valid empty booking list.
**Suggested Layer**: E2E

### TC-404: Clear all when the account is already empty
**Category**: Edge Case
**Priority**: P3
**Preconditions**: Use a disposable test account with zero bookings.
**Steps**:
1. Open **My Bookings**.
2. Select **Clear all bookings** and accept the confirmation.
**Expected Results**: The list remains in the empty state; the operation completes without creating records or showing a false error.
**Business Rule**: Deleting zero owned bookings is safe and leaves the account empty.
**Suggested Layer**: API

### TC-405: Preserve remaining dynamic-event availability when one of multiple bookings is cancelled
**Category**: Edge Case
**Priority**: P2
**Preconditions**: The user owns at least two bookings for the same user-created event, with different quantities.
**Steps**:
1. Record total seats and each booking quantity.
2. Cancel one booking only.
3. Recheck the event's per-user available-seat count and the remaining booking.
**Expected Results**: Only the selected booking is deleted; the other remains, and available seats reflect the quantity released by the cancelled booking only.
**Business Rule**: Dynamic-event availability is computed from the sum of the current user's remaining bookings for that event.
**Suggested Layer**: API

## UI States

### TC-500: Show loading placeholders while the booking list loads
**Category**: UI State
**Priority**: P2
**Preconditions**: The booking-list API response can be delayed in a controlled test.
**Steps**:
1. Sign in and open `/bookings` while delaying the list response.
2. Observe the page before releasing the response.
3. Release the response and wait for the list.
**Expected Results**: Booking-card skeletons appear during loading and are replaced by the loaded cards or empty state; loading placeholders do not remain.
**Business Rule**: The list distinguishes loading from loaded, empty, and error states.
**Suggested Layer**: Component

### TC-501: Show a loading indicator while booking details load
**Category**: UI State
**Priority**: P2
**Preconditions**: The user owns a booking; the detail API response can be delayed.
**Steps**:
1. Navigate to the booking detail URL while delaying its API response.
2. Observe the page before releasing the response.
3. Release the response.
**Expected Results**: A page-level spinner appears while loading and is replaced by the matching details after the response.
**Business Rule**: Detail loading is distinct from not-found and access-denied states.
**Suggested Layer**: Component

### TC-502: Retry after the booking list fails to load
**Category**: UI State
**Priority**: P1
**Preconditions**: The list API can be made to fail once and succeed on retry.
**Steps**:
1. Open `/bookings` with the first list request configured to fail.
2. Verify the error state and select **Retry**.
3. Allow the next request to succeed.
**Expected Results**: **Couldn't load bookings** and its retry action appear after failure; a successful retry replaces the error with the correct list or empty state.
**Business Rule**: A booking-list network failure is surfaced as an error state and can be retried.
**Suggested Layer**: Component

### TC-503: Show cancellation progress and prevent duplicate confirmation
**Category**: UI State
**Priority**: P2
**Preconditions**: The user owns a booking; the cancellation response can be delayed.
**Steps**:
1. Open the cancellation confirmation dialog.
2. Confirm cancellation while delaying the endpoint response.
3. Inspect confirmation controls while the request is pending.
4. Release the response.
**Expected Results**: The confirm control shows a loading state and duplicate submission is prevented. Attempts to dismiss the dialog while cancellation is pending do not hide it or throw a client-side error. On success the dialog closes and the booking is removed.
**Business Rule**: A pending cancellation must not be submitted multiple times or dismissed as though it had not started.
**Suggested Layer**: E2E

### TC-504: Show clear-all progress and disable repeat submission
**Category**: UI State
**Priority**: P2
**Preconditions**: Use a disposable test account with bookings; the clear-all API response can be delayed.
**Steps**:
1. Open **My Bookings**.
2. Accept the native confirmation for **Clear all bookings** while delaying the API response.
3. Inspect the clear-all control while the request is pending.
4. Release the response.
**Expected Results**: The button changes to **Clearing…** and is disabled while the request is pending; after success it returns to its normal label and the empty state appears.
**Business Rule**: Clear-all has an explicit pending state and prevents duplicate requests.
**Suggested Layer**: Component

### TC-505: Display the refund checking state before its result
**Category**: UI State
**Priority**: P2
**Preconditions**: The user owns a booking; the detail page is loaded.
**Steps**:
1. Select **Check eligibility for refund?**
2. Observe the refund area immediately and during the approximately four-second check.
3. Wait for the result.
**Expected Results**: The action is replaced by the checking spinner during evaluation; afterward the spinner is replaced by exactly one eligibility result.
**Business Rule**: Refund status is client-side and transitions from idle to checking to a quantity-dependent result.
**Suggested Layer**: Component
