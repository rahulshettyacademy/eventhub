## Flow: Refund check on booking detail page  (explored 2026-09-29, http://localhost:3000)

### Steps
1. /login - filled email/password, clicked `#login-btn` -> landed on `/`. The hero link is "Browse Events →" (arrow character), so the docs' `'Browse Events ->'` does not match.
2. /bookings - the account had 0 bookings (empty state, "Clear all bookings" button visible). Booked on /events/5 (Probe Event 1, $10) to create test data.
3. /events/5 - booked 1 ticket (ref P-E8KIFZ, booking id 1), then reloaded and booked 2 tickets by clicking "+" once (ref P-TOCLK9, booking id 2). Confirmation shows `.booking-ref`.
4. /bookings - 2 cards, each with "View Details" (an `<a href="/bookings/N">` wrapping a button).
5. /bookings/1 (1 ticket) - clicked check-refund-btn -> the button disappears, the spinner shows "Checking your refund eligibility…", then after about 4.3 s the result reads "Eligible for refund. Single-ticket bookings qualify for a full refund."
6. /bookings/2 (2 tickets) - same flow -> "Not eligible for refund. Group bookings (2 tickets) are non-refundable."

### Verified locators (all counted on the live page; every one matched exactly 1 element)
| Element | Locator | Tier | Matches |
|---|---|---|---|
| Check refund button (idle) | `page.getByTestId('check-refund-btn')` | 1 | 1 |
| Same, by role | `getByRole('button', { name: 'Check eligibility for refund?' })` | 2 | 1 |
| Same, by id | `#check-refund-btn` | 4 | 1 |
| Refund spinner | `page.getByTestId('refund-spinner')` | 1 | 1 while checking, else 0 |
| Spinner text | "Checking your refund eligibility…" | text | 1 |
| Refund result | `page.getByTestId('refund-result')` | 1 | 1 (only one result at a time) |
| Result by id | `#refund-result` | 4 | 1 |
| Refund section heading | `getByRole('heading', { name: 'Refund' })` | 2 | 1 |
| Ticket count | `getByTestId('booking-tickets')` | 1 | 1 (text "1" or "2") |
| Total paid | `getByTestId('booking-total-paid')` | 1 | 1 |
| Cancel booking | `getByTestId('cancel-booking-btn')` (also `#cancel-booking-btn`) | 1 | 1 (confirmed bookings only) |
| Event title | `getByRole('heading', { level: 1 })` | 2 | 1 |
| Booking ref (detail) | `span.font-mono.font-bold` | 5 | 1 (works, but no testid) |
| Booking cards (list) | `getByTestId('booking-card')` (also `#booking-card`) | 1 | 2 (= bookings) |
| Booking id on card | `getByTestId('booking-id')` | 1 | one per card, text "#2" |
| View Details link (list) | `getByRole('link', { name: 'View Details' })` | 2 | one per card |
| Booking ref (list card) | `.booking-ref` | 5 | one per card |

Assertions that worked: `expect(result).toContainText('Eligible for refund')` for 1 ticket, `toContainText('Not eligible for refund')` for >1, and `toContainText('2 tickets')` for the group case. Careful: `getByText(/eligible for refund/i)` also matches "Not eligible for refund". Use the testid and assert the text.

### Drift from docs
- ui-selectors.md and the best-practices doc use `getByRole('link', { name: 'Browse Events ->' })`. The real link is "Browse Events →". A login helper using `->` times out (I hit a 30 s timeout). Update ui-selectors.md, `.claude/rules/playwright-best-practices.md`, and any spec using `->`.
- ui-selectors.md gives `#check-refund-btn`, `#refund-spinner`, `#refund-result` (still valid). The current uncommitted `frontend/app/bookings/[id]/page.tsx` now also has `data-testid` on them, so prefer `getByTestId`. The doc should list the testids.
- ui-selectors.md says the cancel button is "visible on detail page" with no locator. It now has `data-testid="cancel-booking-btn"`.
- Booking detail doc lacks `booking-tickets` and `booking-total-paid` testids. Booking list lacks `booking-card` and `booking-id` testids (only `#booking-card` is documented).
- business-rules.md says the button is "Check Refund Eligibility". The real text is "Check eligibility for refund?".
- business-rules.md says the eligible message is "Single-ticket bookings qualify for a full refund" and the ineligible message is "Group bookings (N tickets) are non-refundable". Both are correct, but they are prefixed with a bold "Eligible for refund." or "Not eligible for refund.".
- The doc's "Increment/Decrement: `button:has-text("+")`" works, but the button label is the real minus sign "−" (U+2212), not "-". The role name for decrement is "−".

### Missing data-testid (suggested)
- Booking ref on detail page (`span.font-mono.font-bold` in `frontend/app/bookings/[id]/page.tsx`): suggest `data-testid="booking-ref"`. The breadcrumb also renders the ref in a `span.font-mono`, so a CSS selector is fragile.
- Booking status badge in the same file: suggest `data-testid="booking-status"`.
- Booking ref on list cards (`.booking-ref` class only): suggest `data-testid="booking-ref"` in the bookings list card component.
- Refund result variants: it is fine as is, but a `data-status="eligible|ineligible"` attribute on `refund-result` would avoid text matching. Optional.

### Business rule checks
- Refund eligible only for 1 ticket: PASS (1 ticket -> "Eligible for refund"; 2 tickets -> "Not eligible for refund. Group bookings (2 tickets) are non-refundable").
- Booking ref first character = event title first character (uppercase): PASS (P-E8KIFZ and P-TOCLK9 for "Probe Event 1").
- Seats drop on booking: NOT VERIFIED. The event page showed "50 / 50 seats" right after the first booking on the same page state, which may be stale client cache. I did not reload to confirm, so I can't call this pass or fail.
- Static-event immutability and cross-user "Access Denied": not in scope, not checked.

### Timing/quirks
- The spinner lasts about 4 s (result appeared at about 4.33 s after the click, from a hard-coded 4000 ms `setTimeout`). The default 5 s assertion timeout is tight, so use `expect(result).toBeVisible({ timeout: 6000 })` (the doc uses 6000 for the spinner too).
- Assert the spinner is visible right after the click (`getByTestId('refund-spinner')`), then wait for the result. Do not use `waitForTimeout`.
- The check button is removed from the DOM after the click (0 matches), and the spinner is removed when the result appears. A test cannot re-check without reloading; the state is client-only and resets on reload.
- The refund UI shows for cancelled bookings too, as far as the source shows; only the cancel button is gated on `status === 'confirmed'`. Not verified live.
- Test data: the account had no bookings, so tests must create their own (max 9 bookings, FIFO pruned). Book a 1-ticket and a 2-ticket booking on a low-price event, and avoid hardcoding `/bookings/1`; grab the id from the "View Details" link's href or from the card's `booking-id`. The bookings page has a "Clear all bookings" button (`getByRole('button', { name: 'Clear all bookings' })`, it has no testid) for clean data.
- Console shows 1 error on page load (not investigated; not related to refund).
- I created 2 bookings (P-E8KIFZ, P-TOCLK9) on Probe Event 1 in the rahulshetty1@gmail.com sandbox and did not clean them up. No repo files were modified.

### Follow-ups (say the word)
- Update ui-selectors.md, business-rules.md and best-practices with the drift above.
- Run `/generate-tests refund check` using this map.
