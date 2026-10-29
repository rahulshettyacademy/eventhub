import { test, expect } from '@playwright/test';

const BASE_URL      = 'https://eventhub.rahulshettyacademy.com';
const API_URL       = 'https://api.eventhub.rahulshettyacademy.com/api';
const USER_EMAIL    = 'rahulshetty1@gmail.com';
const USER_PASSWORD = 'Magiclife1!';

const CUSTOMER = {
  customerName:  'Booking Flow QA',
  customerEmail: 'booking.flow.qa@example.com',
  customerPhone: '9876543210',
};
const QUANTITY = 2;

// Same formatting the app uses for prices (USD, no decimals)
const fmtPrice = (n) =>
  new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(n);

const escapeRegExp = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

// ── Helpers ────────────────────────────────────────────────────────────────────

/**
 * Headers for direct API calls. `Connection: close` stops the request context
 * from reusing a pooled socket: the live API's proxy drops idle keep-alive
 * connections, so the first call after a long UI phase failed with
 * "socket hang up".
 */
const apiHeaders = (token) => ({
  Connection: 'close',
  ...(token && { Authorization: `Bearer ${token}` }),
});

/** Logs in through the API and returns the JWT. */
async function apiLogin(request) {
  const res = await request.post(`${API_URL}/auth/login`, {
    headers: apiHeaders(),
    data: { email: USER_EMAIL, password: USER_PASSWORD },
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).token;
}

/** Puts the JWT where useAuth expects it, so the browser starts logged in. */
async function loginInBrowser(page, token) {
  await page.addInitScript((t) => localStorage.setItem('eventhub_token', t), token);
}

/**
 * Creates a booking on a static (seeded) event with enough personal seats.
 * Static events are shared and immutable, so the test never depends on
 * user-created events left behind by earlier runs.
 */
async function createBooking(request, token) {
  const headers = apiHeaders(token);

  const eventsRes = await request.get(`${API_URL}/events`, { headers, params: { limit: 50 } });
  expect(eventsRes.ok()).toBeTruthy();
  const event = (await eventsRes.json()).data.find((e) => e.isStatic && e.availableSeats >= QUANTITY);
  expect(event, 'a static event with free seats must exist').toBeTruthy();

  const bookingRes = await request.post(`${API_URL}/bookings`, {
    headers,
    data: { eventId: event.id, quantity: QUANTITY, ...CUSTOMER },
  });
  expect(bookingRes.status()).toBe(201);
  const booking = (await bookingRes.json()).data;
  console.log(`Created booking ${booking.bookingRef} (#${booking.id}) for "${event.title}"`);
  return { booking, event };
}

// ── Test Suite ─────────────────────────────────────────────────────────────────

test.describe('Booking Flow — critical E2E paths', () => {
  let token;
  let booking;
  let event;
  let extraBookingIds;   // any further bookings a test creates, for cleanup

  test.beforeEach(async ({ page, request }) => {
    token = await apiLogin(request);
    ({ booking, event } = await createBooking(request, token));
    extraBookingIds = [];
    await loginInBrowser(page, token);
  });

  // Delete only the bookings this test created, so other specs sharing the
  // account keep their data. A 404 here just means the test already removed it.
  test.afterEach(async ({ request }) => {
    for (const id of [booking?.id, ...extraBookingIds].filter(Boolean)) {
      const res = await request.delete(`${API_URL}/bookings/${id}`, { headers: apiHeaders(token) });
      if (![200, 404].includes(res.status())) {
        console.warn(`Cleanup of booking #${id} failed: HTTP ${res.status()}`);
      }
    }
  });

  // TC-001 ───────────────────────────────────────────────────────────────────
  test('TC-001: bookings list shows a card with the booking details', async ({ page }) => {
    // -- Step 1: Open My Bookings from the navbar --
    await page.goto(BASE_URL);
    await page.getByTestId('nav-bookings').click();
    await expect(page).toHaveURL(`${BASE_URL}/bookings`);
    await expect(page.getByRole('heading', { name: 'My Bookings' })).toBeVisible();

    // -- Step 2: Find the card for this booking --
    const card = page.getByTestId('booking-card').filter({
      has: page.locator('.booking-ref', { hasText: booking.bookingRef }),
    });
    await expect(card).toHaveCount(1);

    // -- Step 3: Assert the card's content --
    await expect(card.getByTestId('booking-id')).toHaveText(`#${booking.id}`);
    await expect(card).toContainText('confirmed');
    await expect(card).toContainText(event.title);
    await expect(card).toContainText(`${QUANTITY} tickets`);
    await expect(card).toContainText(event.city);
    await expect(card).toContainText(fmtPrice(parseFloat(event.price) * QUANTITY));
    await expect(card.getByRole('link', { name: 'View Details' })).toBeVisible();
    await expect(card.getByTestId('cancel-booking-btn')).toBeVisible();
  });

  // TC-002 ───────────────────────────────────────────────────────────────────
  test('TC-002: booking detail page shows all sections', async ({ page }) => {
    // -- Step 1: Open the detail page from the list --
    await page.goto(`${BASE_URL}/bookings`);
    const card = page.getByTestId('booking-card').filter({
      has: page.locator('.booking-ref', { hasText: booking.bookingRef }),
    });
    await card.getByRole('link', { name: 'View Details' }).click();
    await expect(page).toHaveURL(`${BASE_URL}/bookings/${booking.id}`);

    // -- Step 2: Breadcrumb and header --
    const breadcrumb = page.getByRole('navigation').filter({ hasText: booking.bookingRef });
    await expect(breadcrumb).toContainText('My Bookings');
    await expect(breadcrumb).toContainText(booking.bookingRef);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(event.title);

    // -- Step 3: Every section is rendered --
    for (const section of ['Event Details', 'Customer Details', 'Payment Summary', 'Refund', 'Booking Information']) {
      await expect(page.getByRole('heading', { name: section, exact: true })).toBeVisible();
    }

    // -- Step 4: Section values match the booking --
    const main = page.getByRole('main');
    await expect(main).toContainText(event.venue);
    await expect(main).toContainText(event.category);
    await expect(main).toContainText(CUSTOMER.customerName);
    await expect(main).toContainText(CUSTOMER.customerEmail);
    await expect(main).toContainText(CUSTOMER.customerPhone);
    await expect(main).toContainText(fmtPrice(parseFloat(event.price) * QUANTITY));
    await expect(main).toContainText(`#${booking.id}`);
    await expect(page.getByTestId('check-refund-btn')).toBeVisible();
  });

  // TC-003 ───────────────────────────────────────────────────────────────────
  test('TC-003: cancelling from the detail page shows a toast, redirects and deletes the booking', async ({ page, request }) => {
    // -- Step 1: Open the detail page --
    await page.goto(`${BASE_URL}/bookings/${booking.id}`);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(event.title);

    // -- Step 2: Open the cancel dialog --
    await page.getByRole('button', { name: 'Cancel Booking' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('Cancel this booking?');
    await expect(dialog).toContainText(`Cancelling ${booking.bookingRef} will release ${QUANTITY} seat(s)`);

    // -- Step 3: Confirm --
    await page.getByTestId('confirm-dialog-yes').click();

    // -- Step 4: Toast and redirect --
    await expect(page.getByText('Booking cancelled successfully')).toBeVisible();
    await expect(page).toHaveURL(`${BASE_URL}/bookings`);

    // -- Step 5: The booking is gone from the list and the API --
    // Wait for the list to finish loading first: while the skeletons show there
    // are no refs at all, so the count-0 check would pass without testing anything
    await expect(
      page.getByTestId('booking-card').first().or(page.getByRole('heading', { name: 'No bookings yet' })),
    ).toBeVisible();
    await expect(page.locator('.booking-ref', { hasText: booking.bookingRef })).toHaveCount(0);
    const res = await request.get(`${API_URL}/bookings/${booking.id}`, { headers: apiHeaders(token) });
    expect(res.status()).toBe(404);
  });

  // TC-004 ───────────────────────────────────────────────────────────────────
  test('TC-004: cancelling from a list card removes only that card and stays on the list', async ({ page, request }) => {
    // -- Step 1: Create a second booking so we can check it survives --
    const { booking: other } = await createBooking(request, token);
    extraBookingIds.push(other.id);

    // -- Step 2: Open the list; both cards are shown --
    await page.goto(`${BASE_URL}/bookings`);
    const cardFor = (ref) => page.getByTestId('booking-card').filter({
      has: page.locator('.booking-ref', { hasText: ref }),
    });
    await expect(cardFor(booking.bookingRef)).toHaveCount(1);
    await expect(cardFor(other.bookingRef)).toHaveCount(1);

    // -- Step 3: Cancel the first booking from its card --
    await cardFor(booking.bookingRef).getByTestId('cancel-booking-btn').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('Cancel this booking?');
    await expect(dialog).toContainText(`This will cancel ${booking.bookingRef} and release ${QUANTITY} seat(s)`);
    await dialog.getByTestId('confirm-dialog-yes').click();

    // -- Step 4: Toast, dialog closes, no redirect --
    await expect(page.getByText('Booking cancelled successfully')).toBeVisible();
    await expect(dialog).toHaveCount(0);
    await expect(page).toHaveURL(`${BASE_URL}/bookings`);

    // -- Step 5: Only the cancelled card is gone --
    await expect(cardFor(booking.bookingRef)).toHaveCount(0);
    await expect(cardFor(other.bookingRef)).toHaveCount(1);
    const res = await request.get(`${API_URL}/bookings/${booking.id}`, { headers: apiHeaders(token) });
    expect(res.status()).toBe(404);
  });

  // TC-005 ───────────────────────────────────────────────────────────────────
  // Note: "Clear all bookings" removes EVERY booking on the shared account,
  // not just the ones this test created.
  test('TC-005: clear all bookings empties the list after confirming', async ({ page, request }) => {
    // -- Step 1: Make sure there are at least two bookings --
    const { booking: other } = await createBooking(request, token);
    extraBookingIds.push(other.id);

    await page.goto(`${BASE_URL}/bookings`);
    await expect(page.getByTestId('booking-card').first()).toBeVisible();

    // -- Step 2: Accept the native confirm, checking its message --
    let confirmMessage = '';
    page.once('dialog', async (d) => {
      confirmMessage = d.message();
      await d.accept();
    });
    await page.getByRole('button', { name: 'Clear all bookings' }).click();

    // -- Step 3: Empty state replaces the list --
    await expect(page.getByRole('heading', { name: 'No bookings yet' })).toBeVisible();
    await expect(page.getByTestId('booking-card')).toHaveCount(0);
    expect(confirmMessage).toBe('Clear all your bookings? This cannot be undone.');

    // -- Step 4: The API agrees nothing is left --
    const res = await request.get(`${API_URL}/bookings`, { headers: apiHeaders(token) });
    const body = await res.json();
    expect(body.pagination.total).toBe(0);
    expect(body.data).toEqual([]);
  });

  // TC-006 ───────────────────────────────────────────────────────────────────
  test('TC-006: booking through the UI then "View My Bookings" lists the new booking first', async ({ page }) => {
    // The beforeEach booking is the older one, so "newest first" is checkable.

    // -- Step 1: Book the same static event through the form (quantity 1) --
    await page.goto(`${BASE_URL}/events/${event.id}`);
    await page.getByLabel('Full Name').fill(CUSTOMER.customerName);
    await page.getByTestId('customer-email').fill(CUSTOMER.customerEmail);
    await page.getByLabel('Phone Number').fill(CUSTOMER.customerPhone);

    // Capture the created booking from the response so cleanup works even if
    // a later assertion fails
    const [bookingResponse] = await Promise.all([
      page.waitForResponse((r) => r.url().endsWith('/api/bookings') && r.request().method() === 'POST'),
      page.getByRole('button', { name: 'Confirm Booking' }).click(),
    ]);
    expect(bookingResponse.status()).toBe(201);
    const created = (await bookingResponse.json()).data;
    extraBookingIds.push(created.id);

    // -- Step 2: Confirmation card shows the new ref --
    await expect(page.getByRole('heading', { name: /Booking Confirmed/ })).toBeVisible();
    await expect(page.locator('.booking-ref')).toHaveText(created.bookingRef);
    expect(created.bookingRef).toMatch(new RegExp(`^${escapeRegExp(event.title[0].toUpperCase())}-[A-Z0-9]{6}$`));
    console.log(`Booked through the UI: ${created.bookingRef}`);

    // -- Step 3: Go to My Bookings from the confirmation --
    await page.getByRole('link', { name: 'View My Bookings' }).click();
    await expect(page).toHaveURL(`${BASE_URL}/bookings`);

    // -- Step 4: The new booking is listed above the older one --
    // Compare relative order, not "first card": the shared account can get
    // bookings from other runs at any moment.
    const cardFor = (ref) => page.getByTestId('booking-card').filter({
      has: page.locator('.booking-ref', { hasText: ref }),
    });
    await expect(cardFor(created.bookingRef)).toContainText('1 ticket');
    await expect(cardFor(booking.bookingRef)).toHaveCount(1);
    const refs = await page.getByTestId('booking-card').locator('.booking-ref').allTextContents();
    expect(refs.indexOf(created.bookingRef)).toBeLessThan(refs.indexOf(booking.bookingRef));
  });
});
