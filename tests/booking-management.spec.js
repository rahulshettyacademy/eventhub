import { test, expect } from '@playwright/test';

const BASE_URL      = 'https://eventhub.rahulshettyacademy.com';
const API_URL       = 'https://api.eventhub.rahulshettyacademy.com/api';
const USER_EMAIL    = 'rahulshetty1@gmail.com';
const USER_PASSWORD = 'Magiclife1!';

// List, detail, cancel, clear-all and "View My Bookings" journeys live in
// booking-flow.spec.js. This file covers the booking-reference rule through
// the real booking form.

const escapeRegExp = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

// ── Helpers ────────────────────────────────────────────────────────────────────

async function login(page) {
  await page.goto(`${BASE_URL}/login`);
  await page.getByPlaceholder('you@email.com').fill(USER_EMAIL);
  await page.getByLabel('Password').fill(USER_PASSWORD);
  await page.locator('#login-btn').click();
  // Home page loads after login — "Browse Events →" link confirms successful auth
  await expect(page.getByRole('link', { name: /Browse Events/i }).first()).toBeVisible();
}

/**
 * Books the first available (non-sold-out) event through the booking form.
 * Calls onCreated(booking) as soon as the API confirms the booking, so the
 * caller can schedule cleanup even if a later assertion fails.
 * Returns { booking, eventTitle }.
 */
async function bookEvent(page, onCreated) {
  await page.goto(`${BASE_URL}/events`);

  // Pick the first card that has a visible "Book Now" button (not sold out)
  const firstCard = page.getByTestId('event-card').filter({
    has: page.getByTestId('book-now-btn'),
  }).first();
  await expect(firstCard).toBeVisible();

  // Capture title before navigating away
  const eventTitle = (await firstCard.locator('h3').textContent())?.trim() ?? '';
  console.log(`Booking event: "${eventTitle}"`);

  await firstCard.getByTestId('book-now-btn').click();
  await expect(page).toHaveURL(/\/events\/\d+/);

  // Fill booking form
  await page.getByLabel('Full Name').fill('Test User');
  await page.locator('#customer-email').fill('testuser@example.com');
  await page.getByPlaceholder('+91 98765 43210').fill('9876543210');

  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().endsWith('/api/bookings') && r.request().method() === 'POST'),
    page.locator('.confirm-booking-btn').click(),
  ]);
  expect(response.status()).toBe(201);
  const booking = (await response.json()).data;
  onCreated(booking);

  // Confirmation card shows the same ref the API returned
  await expect(page.locator('.booking-ref')).toHaveText(booking.bookingRef);
  console.log(`Booking confirmed. Ref: ${booking.bookingRef}`);
  return { booking, eventTitle };
}

// ── Test Suite ─────────────────────────────────────────────────────────────────

test.describe('Booking Management — booking reference', () => {
  let createdBookingId;

  // Delete only the booking this test created; never clear the shared account
  test.afterEach(async ({ page, request }) => {
    if (!createdBookingId) return;
    const token = await page.evaluate(() => localStorage.getItem('eventhub_token'));
    const res = await request.delete(`${API_URL}/bookings/${createdBookingId}`, {
      headers: { Connection: 'close', Authorization: `Bearer ${token}` },
    });
    if (![200, 404].includes(res.status())) {
      console.warn(`Cleanup of booking #${createdBookingId} failed: HTTP ${res.status()}`);
    }
    createdBookingId = undefined;
  });

  // TC-107 ───────────────────────────────────────────────────────────────────
  test('TC-107: booking reference starts with first letter of event title (uppercase)', async ({ page }) => {
    // -- Step 1: Login --
    await login(page);

    // -- Step 2: Book the first available event and capture its title --
    const { booking, eventTitle } = await bookEvent(page, (b) => { createdBookingId = b.id; });

    // -- Step 3: Assert ref starts with the event title's first char --
    const expectedPrefix = eventTitle[0].toUpperCase();
    expect(booking.bookingRef).toMatch(new RegExp(`^${escapeRegExp(expectedPrefix)}-[A-Z0-9]{6}$`));
    console.log(`Ref "${booking.bookingRef}" correctly starts with "${expectedPrefix}-" (event: "${eventTitle}")`);
  });
});
