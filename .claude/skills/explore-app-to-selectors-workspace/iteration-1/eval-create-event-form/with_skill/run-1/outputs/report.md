## Flow: Create event form  (explored 2026-09-29, http://localhost:3000)

Bottom line: the date field is a native `<input type="datetime-local">`, not a text or date-picker widget. The locator in the docs, `getByLabel('Event Date & Time')`, is correct and matches exactly 1 element. The likely causes of a failing date step are the value format, or a date that is not in the future. Both are covered below.

### Steps
1. /login - filled email and password, clicked `#login-btn` -> redirected to `/`. The home link reads "Browse Events →" (a real arrow), not "->".
2. /admin/events (reached via the nav "Admin" button or the footer "Manage Events" link) - the create form `#admin-event-form` is on this page. There is no separate "create event" page.
3. Clicked "+ Add Event" with an empty form -> 6 inline errors: "Title is required", "City is required", "Venue is required", "Event date is required", "Enter a valid price (≥ 0)", "Must have at least 1 seat". Category has a default, so it never errors.
4. Filled date `2020-01-01T10:00` -> submit shows "Must be a future date".
5. Tried `fill('12/25/2026 10:00')` on the date -> Playwright throws "Malformed value". A datetime-local input only accepts `YYYY-MM-DDTHH:mm`.
6. Filled a valid future date `2026-10-06T10:30` plus the other fields -> POST /api/events sent, toast "Event created!", the form reset (date is empty again) and the new event is listed once on the page.

### Verified locators
| Element | Locator | Priority tier | Matches |
|---|---|---|---|
| Form | `getByTestId('admin-event-form')` or `#admin-event-form` | testid | 1 |
| Title | `getByTestId('event-title-input')` (also `#event-title-input`) | testid | 1 |
| Description | `#admin-event-form textarea` (no label link, no id, no testid) | CSS | 1 |
| Category | `getByLabel('Category')` (a `<select>`, id `category`, default Conference; options Conference/Concert/Sports/Workshop/Festival) | label | 1 |
| City | `getByLabel('City')` (placeholder "e.g. Bangalore") | label | 1 |
| Venue | `getByLabel('Venue')` | label | 1 |
| Date & time | `getByLabel('Event Date & Time')` (input type datetime-local, id `event-date-&-time`) | label | 1 |
| Price | `getByLabel('Price ($)')` (number, min 0, step 0.01) | label | 1 |
| Total seats | `getByLabel('Total Seats')` (number, min 1) | label | 1 |
| Image URL | `getByLabel('Image URL (optional)')` (type url, optional) | label | 1 |
| Add button | `getByTestId('add-event-btn')` (also `#add-event-btn`) | testid | 1 |
| Field errors | `#admin-event-form p.text-red-600` (no testid) | CSS | one per error |
| Success toast | `getByText('Event created!')` | text | 1 |

Date gotchas (my best guess at your failure, since I have not seen your test):
- Fill format must be exactly `YYYY-MM-DDTHH:mm` (local time). Anything like `DD/MM/YYYY`, a value with seconds or a `Z`, or a `.type()`/keyboard approach will fail or be malformed.
- Do not use `#event-date-&-time`. The id is auto-generated from the label and contains `&`, `-` and spaces, so it needs escaping. Use the label.
- The date must be in the future at submit time. A hardcoded date will start failing once it passes, and today is 2026-09-29. Compute it, e.g. now + 7 days.
- Browser timezone here is Asia/Calcutta. The form converts local time to UTC on submit (typed 10:30 became `2026-10-06T05:00:00.000Z`), so do not assert the raw UTC string against the typed value.
- After a successful create the date input is cleared, so do not read it back to assert.

### Drift from docs
- ui-selectors.md "Admin Event Form" section: all listed locators still resolve correctly. No drift found there. It does not mention `data-testid` on the title, form or add button (available and preferred over the id), that the date is datetime-local, or the format requirement (`.claude/references/eventhub-domain/ui-selectors.md`).
- playwright-best-practices.md (and CLAUDE.md flows) use `getByRole('link', { name: 'Browse Events ->' })` as the login check. The real link text is "Browse Events →" (arrow character), so that assertion times out after login. It timed out for me too. This may be what breaks the test before it reaches the date field. Fix: use `→`, or a regex like `/Browse Events/`.
- The docs do not say the form lives at /admin/events.

### Missing data-testid (suggested)
- Date input: `data-testid="event-date-input"` in `frontend/components/events/EventForm.jsx`. `Input` passes props through, so it is a one-line change.
- Same for city, venue, price, seats, category and description (`event-city-input`, etc.). The description textarea has no id or label link, so it is the weakest locator.
- Field errors: `data-testid="<field>-error"` in `frontend/components/ui/Input.tsx`.

### Business rule checks
- Event date must be a future date: PASS (client-side "Must be a future date" for 2020-01-01; the backend rule "Event date must be in the future" was not exercised directly).
- Required fields validated client-side: PASS.
- Create shows "Event created!": PASS.
- Max 6 user-created events with FIFO pruning: not checked. I created one event, "Explore Event 1790689499381", and did not delete it.

### Timing/quirks
- The form uses `noValidate`, so validation errors are React state, not browser tooltips. They appear immediately on submit, with no waiting.
- Toast "Event created!" appears within the 5s assertion timeout.
- Console had 1 error on /login page load, which I did not investigate. Nothing on the form.

### Follow-ups (not done)
I changed no repo files. I can update ui-selectors.md with the date format notes and the "→" drift, or add the suggested data-testids, or run `/generate-tests create event` with this map. Say the word.
