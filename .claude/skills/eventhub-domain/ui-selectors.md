# EventHub UI Selectors Reference

Selectors are written in Playwright syntax. For Selenium Java, translate them as described in `selenium-best-practices` §2: `getByTestId('x')` → `Locators.testId("x")`, `#id` → `By.id("id")`, `getByPlaceholder('p')` → `By.cssSelector("[placeholder='p']")`, and labels → the input's id.

Prefer `data-testid` hooks (listed at the end) over the other selectors whenever one exists.

## Login Page
- Email input: `getByPlaceholder('you@email.com')` or `getByLabel('Email')` (id `email`)
- Password input: `getByLabel('Password')` or `getByPlaceholder('••••••')` (id `password`)
- Login button: `#login-btn`

## Home Page
- Browse Events link: `getByRole('link', { name: 'Browse Events →' })` (tests wait for it to confirm login finished)
- Navigation: `getByTestId('nav-home')`, `getByTestId('nav-events')`, `getByTestId('nav-bookings')`
- Logged-in user: `getByTestId('user-email-display')`; logout: `getByTestId('logout-btn')`

## Register Page
- Email: `getByTestId('register-email')`
- Password: `getByTestId('register-password')`
- Submit: `getByTestId('register-btn')`

## Events Page
- Event cards: `getByTestId('event-card')`
- Book Now button: `getByTestId('book-now-btn')` (inside card)
- Sandbox banner: `getByText(/sandbox holds up to/i)`
- Category/City/Search filters: form controls

## Event Detail / Booking Form
- Ticket count display: `#ticket-count`
- Increment/Decrement: `button:has-text("+")` / `button:has-text("-")`
- Full Name: `getByLabel('Full Name')` (id `customerName`)
- Email: `#customer-email` or `getByTestId('customer-email')`
- Phone: `getByPlaceholder('+91 98765 43210')` (id `phone`)
- Confirm: `.confirm-booking-btn` (id `confirm-booking`)
- Booking Ref on the confirmation card: `.booking-ref` (`data-testid="booking-ref"` is in the source but not deployed to the live site yet)

## Admin Event Form
- Form: `getByTestId('admin-event-form')`
- Title: `#event-title-input` or `getByTestId('event-title-input')`
- Description: `#admin-event-form textarea`
- City: `getByLabel('City')`
- Venue: `getByLabel('Venue')`
- Date: `getByLabel('Event Date & Time')`
- Price: `getByLabel('Price ($)')`
- Seats: `getByLabel('Total Seats')`
- Add button: `#add-event-btn` or `getByTestId('add-event-btn')`
- Event rows: `getByTestId('event-table-row')`, with `edit-event-btn` / `delete-event-btn` inside

## Bookings Page
- Booking cards: `getByTestId('booking-card')`
- Booking ref on a card: `.booking-ref` (`data-testid="booking-ref"` is in the source but not deployed to the live site yet). Filter cards by it on the shared account
- Booking id on a card: `getByTestId('booking-id')`
- Cancel from a card: `getByTestId('cancel-booking-btn')`, then confirm with `getByTestId('confirm-dialog-yes')` in the "Cancel this booking?" dialog
- View Details link: `getByRole('link', { name: 'View Details' })`
- Clear all: `getByRole('button', { name: 'Clear all bookings' })` at the top of the page; it opens a native `confirm()`

## Booking Detail Page
- Booking ref: `span.font-mono.font-bold` (no `data-testid` yet)
- Event title: `h1`
- Check refund: `#check-refund-btn` or `getByTestId('check-refund-btn')`
- Refund spinner: `#refund-spinner` or `getByTestId('refund-spinner')`
- Refund result: `#refund-result` or `getByTestId('refund-result')`
- Cancel button: `getByRole('button', { name: 'Cancel Booking' })` (no `data-testid` on this page), then confirm with `getByTestId('confirm-dialog-yes')` in the "Cancel this booking?" dialog

## All `data-testid` Hooks in the Frontend
`nav-home`, `nav-events`, `nav-bookings`, `user-email-display`, `logout-btn`, `register-email`, `register-password`, `register-btn`, `event-card`, `book-now-btn`, `customer-email`, `booking-ref` (source only, not yet live), `admin-event-form`, `event-title-input`, `add-event-btn`, `event-table-row`, `edit-event-btn`, `delete-event-btn`, `booking-card`, `booking-id`, `check-refund-btn`, `refund-spinner`, `refund-result`, `cancel-booking-btn`, `confirm-dialog-yes`
