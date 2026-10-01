## Flow: Book a ticket + booking ref / seat count rules  (explored 2026-09-29, http://localhost:3000)
### Steps
1. /login - filled Email/Password, clicked `#login-btn` -> redirected to `/`.
2. /events - 5 `event-card` / 5 `book-now-btn`. Chose static event "World Tech Summit" (/events/1).
3. /events/1 - "Available" showed `500 / 500 seats`. Clicked "+" once (qty 2, `#ticket-count` = 2), filled name/email/phone, clicked `#confirm-booking`.
4. Confirmation panel on same page: ref `W-MN92NN`, Tickets 2, Total $3,000. Seat text on the page still said 500 / 500 (stale until reload).
5. Reload /events/1 -> `498 / 500 seats`.
6. /bookings - 3 cards, new card ref `W-MN92NN`; detail links /bookings/N.
7. /bookings/3 - h1 "World Tech Summit", ref W-MN92NN, tickets 2, total $3,000. Clicked Cancel Booking -> dialog "Cancelling W-MN92NN will release 2 seat(s)..." -> "Yes, cancel it" -> redirected to /bookings.
8. Reload /events/1 -> `500 / 500 seats`.

### Verified locators
| Element | Locator | Tier | Matches |
|---|---|---|---|
| Login button | `#login-btn` | ID | 1 |
| Event card / Book Now | `getByTestId('event-card')` / `getByTestId('book-now-btn')` | testid | 5 / 5 (one per card) |
| Ticket count | `#ticket-count` | ID | 1 |
| Increment | `getByRole('button', {name:'+'})` | role | 1 (decrement "−" is U+2212, not "-") |
| Full Name | `getByLabel('Full Name*')` / `getByPlaceholder('Your full name')` | label | 1 |
| Email | `getByTestId('customer-email')` (also `#customer-email`) | testid | 1 |
| Phone | `getByPlaceholder('+91 98765 43210')` | placeholder | 1 |
| Confirm | `#confirm-booking` (also `.confirm-booking-btn`) | ID | 1 |
| Confirmation ref | `.booking-ref` | CSS class | 1 |
| Confirmation tickets/total | `getByTestId('confirmation-tickets')` / `('confirmation-total')` | testid | 1 each |
| Seats text | `getByText(/\d+ \/ \d+ seats/)` | text | 1 (no testid) |
| Booking cards | `getByTestId('booking-card')` | testid | 3 |
| Detail ref | `span.font-mono.font-bold` | CSS | 1 |
| Detail tickets / total | `getByTestId('booking-tickets')` / `('booking-total-paid')` | testid | 1 each |
| Cancel | `getByTestId('cancel-booking-btn')` | testid | 1 |
| Cancel dialog confirm | `getByTestId('confirm-dialog-yes')` (from MCP-generated code; accessible name "Yes, cancel it") | testid | 1 |

### Drift from docs
- ui-selectors.md lists `#booking-card`: real UI has 3 elements with that ID (duplicate IDs); use `getByTestId('booking-card')` instead (update ui-selectors.md).
- ui-selectors.md says Increment/Decrement `button:has-text("+")`/`("-")`: prefer role; the decrement label is "−" (U+2212) and is disabled at qty 1.
- ui-selectors.md says Confirm is `.confirm-booking-btn`: `#confirm-booking` also exists; Email has a `data-testid="customer-email"` not documented.
- Not documented: `confirmation-tickets`, `confirmation-total`, `booking-tickets`, `booking-total-paid`, `booking-id`, `nav-*`, `logout-btn`, `user-email-display`, cancel dialog (heading "Cancel this booking?", buttons Cancel / Yes, cancel it).
- `.booking-ref` on the /bookings list returned refs for each card (3 matches, one per card).
- Not verified by me: `#login-btn` matches docs (OK).

### Missing data-testid (suggested)
- Seats available text: `data-testid="event-seats"` in frontend/app/events/[id]/page.tsx.
- Ticket count / "+" / "−" buttons: `data-testid="qty-increment"`, `qty-decrement`.
- Confirmation booking ref: `data-testid="confirmation-ref"` (currently only `.booking-ref`); detail page ref: `data-testid="booking-ref"` in frontend/app/bookings/[id]/page.tsx.
- Confirm booking button: `data-testid="confirm-booking-btn"`.

### Business rule checks
- Booking ref first char = event title first char (uppercase): PASS ("World Tech Summit" -> `W-MN92NN`; other existing bookings for "Probe Event" show `P-...`).
- Seats drop on booking: PASS (500 -> 498 for 2 tickets, seen after reload).
- Seats restored on cancel: PASS (498 -> 500 after cancelling).
- Only checked with a static event and quantity 2; the FIFO pruning, refund eligibility and cross-user rules were out of scope and not tested.

### Timing/quirks
- The seat count on the event page is NOT updated on the confirmation view (still 500 / 500); needs a reload/refetch. Tests must reload before asserting seats.
- Cancelling redirects to /bookings.
- One console error appears on /login and a warning on other pages (not investigated).
- Cleanup: my test booking was cancelled; state is back to 500/500.

### Follow-ups offered
- Update ui-selectors.md with the drift above; run `/generate-tests booking-flow` with this map. No repo files were edited.
