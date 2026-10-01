# Create Event form: what is actually there

Location: http://localhost:3000/admin/events (logged in as rahulshetty1@gmail.com). The form is on the Admin page, not a separate /create route.

## Date field
- Label: "Event Date & Time*"
- Element: `<input type="datetime-local" required>`, id is literally `event-date-&-time` (contains `&` and `-`). There is NO data-testid, name, or placeholder. No min/max attribute.
- Likely cause of your failure:
  1. `page.locator('#event-date-&-time')` is an invalid/incorrect CSS id selector. Use `page.locator('#event-date-\\&-time')`, `page.locator('[id="event-date-&-time"]')`, or (best) `page.getByLabel('Event Date & Time')`.
  2. Value format must be `YYYY-MM-DDTHH:mm`, e.g. `2027-12-25T18:30`. Typing `25/12/2026` gets silently discarded (value stays empty).
  3. The app validates client-side: a past date shows "Must be a future date" and no event is created (verified with 2020-01-01). Do not hardcode a date that has become past; generate one dynamically (e.g. now + 30 days).
- Verified: `.fill('2027-12-25T18:30')` then clicking Add Event gives "Event created!" and the event appears in the list.

## Other fields (all verified)
| Field | Element | Locator |
|---|---|---|
| Title* | text, required, placeholder "Event title" | `getByTestId('event-title-input')` / `#event-title-input` |
| Description | textarea, optional, placeholder "Describe the event…" | `getByPlaceholder('Describe the event…')` (no id/testid) |
| Category* | select, default "Conference" | `#category` |
| City* | text, required, placeholder "e.g. Bangalore" | `#city` |
| Venue* | text, required, placeholder "Venue name & address" | `#venue` |
| Price ($)* | number, min=0, placeholder "0.00" | id `price-($)` -> `getByLabel('Price ($)')` |
| Total Seats* | number, min=1, placeholder "e.g. 500" | `#total-seats` |
| Image URL (optional) | type=url, placeholder "https://…" | id `image-url-(optional)` -> `getByLabel('Image URL')` |
| Submit | button "+ Add Event" | `getByTestId('add-event-btn')` |

Existing-event rows also have `edit-event-btn` and `delete-event-btn` testids (Edit / Delete).

Note: several IDs are auto-generated from labels and contain special characters (`&`, `(`, `$`, `)`), so prefer getByLabel for those.

## Side effect
I created one event titled "Probe Event 1" (city Pune, 2027-12-25 18:30) in the test user's sandbox. Delete it if unwanted. No repo files were changed.
