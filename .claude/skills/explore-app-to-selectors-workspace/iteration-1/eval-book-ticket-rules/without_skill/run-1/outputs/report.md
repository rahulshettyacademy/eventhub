# Book-a-ticket exploration: booking ref and seat count rules

Result: both rules hold, plus seat restore on cancel.

Flow (logged in as rahulshetty1@gmail.com):
- Event: "Probe Event 1" (/events/5), showing 47 / 50 seats.
- Booked 2 tickets (clicked "+" once, filled Full Name / Email / Phone, clicked "Confirm Booking").
- Confirmation panel showed Booking Ref P-LVWYYE, Tickets 2, Total $20.

Checks:
1. Booking ref: first char "P" equals event title first char "P" (uppercase). Format is `<X>-<6 alphanumerics>`. Same for the other bookings on the list (P-TOCLK9, P-E8KIFZ). PASS.
2. Seat reduction: after reload the event page showed 45 / 50 (47 - 2). The confirmation view does not refresh the seat text itself (still 47 until reload). PASS.
3. Seat restore: cancelled the booking on /bookings/4. Event page then showed 47 / 50. PASS.

Selector and behaviour notes:
- Event detail: "Full Name*", "Email*", "Phone Number*" labels work with getByLabel; "+" / "-" buttons (max 10, "-" disabled at 1); getByRole('button', {name:'Confirm Booking'}). Seats text: "N / 50 seats" (getByText).
- Login link text after login is "Browse Events →" (arrow character), not "->" as in playwright-best-practices.md. The login helper there would time out.
- Cancel on the bookings list does not cancel directly. It opens a confirm modal. Detail page has data-testid="cancel-booking-btn" (from source), then click "Yes, cancel it". No native dialog is used.
- After cancel, the app redirects to /bookings.
- Bookings list: cards show ref, status, #id, tickets count, total.

Side effects: test data was created and cancelled for the test user (final state of Probe Event 1 seats back to 47). Two older bookings (P-TOCLK9, P-E8KIFZ) remain. No repo files modified. Browser closed.
