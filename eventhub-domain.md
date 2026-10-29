---
name: eventhub-domain
description: EventHub application domain knowledge — business rules, API endpoints, data models, user flows, UI selectors, and error scenarios. Use when writing tests, reviewing code, creating scenarios, or answering questions about how EventHub works.
user-invocable: false
---

# EventHub Domain Knowledge

## Overview
EventHub is a full-stack event ticket booking platform. Users browse events, book tickets, and manage bookings. Each user operates in an isolated sandbox with limits on events and bookings.

## Tech Stack
- **Frontend**: Next.js 14 (App Router), React 18, TypeScript, Tailwind CSS, React Query v5
- **Backend**: Express.js 4.21, Prisma ORM 5.22, MySQL 8+
- **Auth**: JWT (7-day expiry), bcryptjs password hashing
- **Testing**: Playwright E2E (`tests/`, JavaScript, Chromium) and Selenium WebDriver E2E (`selenium/`, Core Java + TestNG + Maven, Chrome). Each suite runs serially against the live site. They use different users (Playwright: `rahulshetty1@gmail.com` / `@yahoo.com`; Selenium: its own `eventhub-selenium-*@qa-sandbox.dev` pair), so the two suites can run in parallel
- **API Docs**: Swagger UI at `/api/docs`

## Test Environment
- **Site**: `https://eventhub.rahulshettyacademy.com`
- **API**: `https://api.eventhub.rahulshettyacademy.com/api` (the site's own `/api` path serves the Next.js app, not the backend)
- **Coding standards**: `playwright-best-practices` (Playwright) and `selenium-best-practices` (Selenium Java). This skill holds the facts both suites assert on; those skills hold how to write the tests

## Architecture
```
frontend/ (Next.js 14)           backend/ (Express.js)
├── app/                         ├── src/
│   ├── page.tsx (Home)          │   ├── routes/       (HTTP layer)
│   ├── login/                   │   ├── controllers/  (Request handling)
│   ├── register/                │   ├── services/     (Business logic)
│   ├── events/                  │   ├── repositories/ (Data access)
│   │   ├── page.tsx (List)      │   ├── validators/   (Input validation)
│   │   └── [id]/page.tsx        │   ├── middleware/    (Auth, errors)
│   ├── bookings/                │   └── utils/        (Error classes)
│   │   ├── page.tsx (List)      ├── prisma/
│   │   └── [id]/page.tsx        │   ├── schema.prisma
│   └── admin/                   │   └── seed.js
│       ├── events/page.tsx      └── app.js
│       └── bookings/page.tsx
├── components/
├── lib/
│   ├── api/ (client, endpoints)
│   └── hooks/ (useAuth, useEvents, useBookings)
└── types/
```

## Data Models

### User
| Field | Type | Notes |
|---|---|---|
| id | Int (PK) | Auto-increment |
| email | String | Unique |
| password | String | bcrypt hashed |
| events | Event[] | User's created events |
| bookings | Booking[] | User's bookings |

### Event
| Field | Type | Notes |
|---|---|---|
| id | Int (PK) | Auto-increment |
| title | String | Required |
| description | Text | Optional |
| category | String | Conference/Concert/Sports/Workshop/Festival |
| venue | String | Required |
| city | String | Bangalore/Mumbai/Hyderabad/Delhi/Chennai |
| eventDate | DateTime | Must be future date |
| price | Decimal | Per ticket, >= 0 |
| totalSeats | Int | >= 1 |
| availableSeats | Int | Dynamic for user events |
| isStatic | Boolean | true = seeded event, immutable |
| userId | Int? | null for static events |

### Booking
| Field | Type | Notes |
|---|---|---|
| id | Int (PK) | Auto-increment |
| eventId | Int (FK) | Cascade delete with Event |
| userId | Int (FK) | Cascade delete with User |
| customerName | String | Min 2 chars |
| customerEmail | String | Valid email format |
| customerPhone | String | Min 10 digits |
| quantity | Int | 1-10 tickets |
| totalPrice | Decimal | price x quantity |
| status | String | Always "confirmed" |
| bookingRef | String | Unique, format: `[FIRST_LETTER]-[6_RANDOM]` |

## Detailed Knowledge (Sub-Files)

Load these based on what the current task needs:

- **Business rules & validation logic** → read `./business-rules.md`
- **API endpoints & error codes** → read `./api-reference.md`
- **UI selectors for test automation** → read `./ui-selectors.md`
- **User flows, test scenarios & test data** → read `./user-flows.md`

---

# EventHub Business Rules

## 1. User Journey
- User signs up or signs in -> browses events -> selects an event -> books tickets
- On booking: seat count reduces immediately, booking reference is generated
- Booking reference first character MUST match the event title's first character (e.g., Event "Tech Summit" -> Ref starts with "T")
- After booking, user can view bookings via "View My Bookings" link or "View Details"
- User can cancel (delete) individual bookings or "Clear All Bookings" in one action
- Users can also create events from the Admin UI or via API

## 2. User Sandbox Isolation
- Each user only sees their own dynamic events and bookings
- Static events (seeded) are shared across all users
- Cross-user access to bookings returns 403 Forbidden ("Access Denied")
- Deleting a user cascades to their events and bookings

## 3. Event Limits (FIFO Pruning)
- Max **6 user-created events** per account
- When limit reached, the OLDEST event is automatically deleted (FIFO replacement)
- Static events are not counted toward this limit
- Static events cannot be edited or deleted
- Events page shows 12 events per page, with pagination

## 4. Booking Limits (FIFO Pruning)
- Max **9 bookings** per user
- When limit reached, the OLDEST booking is automatically deleted (FIFO replacement)
- The bookings list never holds more than 9 bookings, because of the limit above
- Booking deletion (cancellation) immediately frees seats
- "Clear all bookings" (top of the bookings page, behind a native `confirm()`) removes all bookings in one go. The page labels it "Do this often for clean test data"
- Test impact: the test account is shared by every run, so tests must delete only the bookings they created and must not use "Clear all bookings" as cleanup

## 5. Sandbox Warning Banner
- **Events page only**: the banner appears when more than 5 events are listed (6 or more). Text: "Your sandbox holds up to 9 bookings and you can create up to 6 custom events. When either limit is reached, the oldest entry is automatically replaced."
- It's hidden when 5 or fewer events are listed (`sandbox-banner.spec.js` checks 6 shown, 4 hidden)
- The bookings page has no sandbox banner

## 6. Per-User Seat Availability
- For static events: `availableSeats` is a fixed DB field
- For dynamic (user-created) events: computed as `totalSeats - sum(user's booking quantities for that event)`
- This allows the same user to book the same event multiple times for testing
- Seat count reduces immediately on booking confirmation

## 7. Booking Reference Format
- Pattern: `[FIRST_LETTER]-[6_RANDOM_ALPHANUMERIC]`
- **First letter comes from the event title (uppercase)** - this is a key business rule to validate
- Example: Event "Tech Summit" -> Ref: `T-A3B2C1`
- Guaranteed unique via collision retry

## 8. Refund Eligibility (Client-Side Logic)
- **Single ticket bookings (quantity = 1) -> Eligible for refund** with message "Single-ticket bookings qualify for a full refund"
- **Multi-ticket bookings (quantity > 1) -> NOT eligible for refund** with message "Group bookings (N tickets) are non-refundable"
- Displays a **4-second spinner** animation before revealing the result
- This is frontend-only logic (no backend API for refund)
- Accessible via "Check Refund Eligibility" button on booking detail page

## 9. Price Calculation
- `totalPrice = event.price x quantity`
- Price is per-ticket in the Event model

---

# EventHub User Flows & Test Data

## Flow 1: Registration & Login
1. Navigate to /register
2. Enter email (must be unique) and password (min 6 chars)
3. Submit -> JWT issued -> redirected to home
4. Or: Navigate to /login -> enter credentials -> JWT issued -> home

## Flow 2: Browse & Filter Events
1. Login -> navigate to /events
2. Use search bar (searches title, description, venue)
3. Filter by category dropdown (Conference, Concert, etc.)
4. Filter by city dropdown (Bangalore, Mumbai, etc.)
5. Paginate (12 events per page)
6. Click "Book Now" on any event card

## Flow 3: Book an Event
1. From event card -> click "Book Now" -> navigate to /events/:id
2. See event details (title, date, venue, price, available seats)
3. Select quantity (1-10) using +/- buttons
4. Fill customer form: name, email, phone
5. Click "Confirm Booking"
6. See confirmation card with booking reference
7. Navigate to "View My Bookings" or "Browse Events"

## Flow 4: Manage Bookings
1. Navigate to /bookings
2. See list of all bookings with details
3. Click "View Details" -> /bookings/:id
4. See full booking info + event details
5. Check refund eligibility (spinner + result)
6. Cancel booking (delete)
7. Or: "Clear all bookings" from bookings list

## Flow 5: Admin - Manage Events
1. Navigate to /admin/events
2. Fill event creation form (title, category, city, venue, date, price, seats)
3. Submit -> "Event created!" toast
4. See list of user-created events
5. Edit existing events (update form)
6. Delete events (with cascade to bookings)

## Flow 6: Cross-User Security
1. User A creates a booking
2. User A captures booking ID
3. Switch to User B (clear localStorage, re-login)
4. User B navigates to /bookings/:userA_booking_id
5. Sees "Access Denied" message

---

## Test Data

### Seeded Data (10 Static Events)
Run `npm run seed` to insert:
- Tech Conference Bangalore (Conference, 500 seats, $1499)
- Bollywood Night Mumbai (Concert, 1000 seats, $999)
- IPL Cricket Finals (Sports, 40000 seats, $2499)
- Digital Marketing Workshop (Workshop, 100 seats, $299)
- Holi Festival Delhi (Festival, 5000 seats, $199)
- AI Summit Hyderabad (Conference, 300 seats, $1999)
- Classical Music Evening (Concert, 200 seats, $799)
- Marathon Chennai (Sports, 10000 seats, $49)
- Photography Workshop (Workshop, 50 seats, $399)
- Food Festival Bangalore (Festival, 2000 seats, $149)

### Test Accounts
| Account    | Email                    | Password    |
|------------|--------------------------|-------------|
| Gmail User | rahulshetty1@gmail.com   | Magiclife1! |
| Yahoo User | rahulshetty1@yahoo.com   | Magiclife1! |

---

# EventHub API Reference

## Authentication (No Auth Required)
| Method | Endpoint          | Body                    | Response                          |
|--------|-------------------|-------------------------|-----------------------------------|
| POST   | /api/auth/register| { email, password }     | { token, user: { id, email } }    |
| POST   | /api/auth/login   | { email, password }     | { token, user: { id, email } }    |
| GET    | /api/auth/me      | -                       | { user: { userId, email } }       |

## Events (Bearer Token Required)
| Method | Endpoint        | Query/Body                         | Response                     |
|--------|-----------------|------------------------------------|------------------------------|
| GET    | /api/events     | ?search, category, city, page, limit | { data: Event[], pagination }|
| GET    | /api/events/:id | -                                  | { data: Event }              |
| POST   | /api/events     | CreateEventInput                   | { data: Event }              |
| PUT    | /api/events/:id | UpdateEventInput                   | { data: Event }              |
| DELETE | /api/events/:id | -                                  | { message }                  |

## Bookings (Bearer Token Required)
| Method | Endpoint              | Query/Body                    | Response                      |
|--------|-----------------------|-------------------------------|-------------------------------|
| GET    | /api/bookings         | ?eventId, status, page, limit | { data: Booking[], pagination}|
| GET    | /api/bookings/:id     | -                             | { data: Booking }             |
| GET    | /api/bookings/ref/:ref| -                             | { data: Booking }             |
| POST   | /api/bookings         | CreateBookingInput            | { data: Booking }             |
| DELETE | /api/bookings         | -                             | Clear all user bookings       |
| DELETE | /api/bookings/:id     | -                             | Cancel single booking         |

## Error Scenarios
| Scenario                  | HTTP Code | Message                              |
|---------------------------|-----------|--------------------------------------|
| Invalid login credentials | 401       | Invalid credentials                  |
| Duplicate email register  | 400       | Email already registered             |
| Missing auth token        | 401       | Unauthorized                         |
| Cross-user booking access | 403       | Forbidden / Access Denied            |
| Edit static event         | 403       | Cannot modify static events          |
| Insufficient seats        | 400       | Insufficient seats available         |
| Invalid event date (past) | 400       | Event date must be in the future     |
| Missing required fields   | 400       | Validation error details             |

---

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
- Booking Ref on the confirmation card: `getByTestId('booking-ref')` (also `.booking-ref`)

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
- Booking ref on a card: `getByTestId('booking-ref')` (also `.booking-ref`). Filter cards by it on the shared account
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
`nav-home`, `nav-events`, `nav-bookings`, `user-email-display`, `logout-btn`, `register-email`, `register-password`, `register-btn`, `event-card`, `book-now-btn`, `customer-email`, `booking-ref`, `admin-event-form`, `event-title-input`, `add-event-btn`, `event-table-row`, `edit-event-btn`, `delete-event-btn`, `booking-card`, `booking-id`, `check-refund-btn`, `refund-spinner`, `refund-result`, `cancel-booking-btn`, `confirm-dialog-yes`
