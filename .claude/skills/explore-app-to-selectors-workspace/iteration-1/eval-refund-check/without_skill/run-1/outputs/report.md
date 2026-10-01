# Refund check on booking detail page: selectors (verified live)

Page: `/bookings/:id` (numeric booking ID, e.g. `/bookings/1`). Login as rahulshetty1@gmail.com. Verified with a 1-ticket booking (#1) and a 2-ticket booking (#2).

## Selectors
| Element | Preferred | Also works |
|---|---|---|
| Check button | `getByTestId('check-refund-btn')` | `#check-refund-btn`, `getByRole('button', { name: 'Check eligibility for refund?' })` |
| Spinner | `getByTestId('refund-spinner')` | `#refund-spinner` (text "Checking your refund eligibility…") |
| Result (both outcomes) | `getByTestId('refund-result')` | `#refund-result` |
| Refund section heading | `getByRole('heading', { name: 'Refund', level: 2 })` | |
| Ticket count | `getByTestId('booking-tickets')` | |
| Total paid | `getByTestId('booking-total-paid')` | |
| Cancel booking | `getByTestId('cancel-booking-btn')` | `#cancel-booking-btn` |

On the bookings list, `booking-card` and `booking-id` test IDs exist; each card has a "View Details" link that goes to `/bookings/:id`.

## Behaviour observed
1. Before the click, only the button is present. There is no spinner and no result.
2. After the click, the button is removed from the DOM and the spinner appears. It lasts about 3-5 seconds (I did not time it precisely). The existing best-practices doc uses a 6000ms timeout for it.
3. The spinner disappears and `refund-result` appears. The button does not come back, so the check can only be run once per page load.
4. `refund-result` uses the same test ID and ID for both outcomes. Assert on its text:
   - 1 ticket: "Eligible for refund. Single-ticket bookings qualify for a full refund."
   - 2 tickets: "Not eligible for refund. Group bookings (2 tickets) are non-refundable." The number in parentheses is the quantity.
5. The logic is client-side and based on quantity only, so nothing in the check goes through the backend.

## Suggested test skeleton
```js
await page.goto(`${BASE_URL}/bookings/${id}`);
await page.getByTestId('check-refund-btn').click();
await expect(page.getByTestId('refund-spinner')).toBeVisible();
await expect(page.getByTestId('refund-spinner')).not.toBeVisible({ timeout: 6000 });
await expect(page.getByTestId('refund-result')).toContainText('Eligible for refund');
```

Caveats:
- `toContainText('Eligible for refund')` also matches "Not eligible for refund" only if the case is ignored. It is case-sensitive, so it does not match here. To be strict, use `/^Eligible for refund/` or assert "Not eligible" for the group case.
- Create the bookings in the test (1 ticket and 2 tickets) rather than hardcoding IDs. My test data was "Probe Event 1" bookings.
- The console showed one error on the login page. I did not investigate it and it did not affect the flow.
