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
