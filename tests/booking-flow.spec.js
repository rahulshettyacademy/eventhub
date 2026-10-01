import { test, expect } from '@playwright/test';

// ── Test data ─────────────────────────────────────────────────────────────────

const USER_EMAIL    = 'rahulshetty1@gmail.com';
const USER_PASSWORD = 'Magiclife1!';

// High-capacity static event (10,000 seats) — picked deliberately so repeated
// test runs across a shared sandbox account never hit "Sold Out".
// NOTE: backend/prisma/seed.js seeds only 3 static events (World Tech Summit,
// Hollywood Monsoon Night — Los Angeles, Dilli Diwali Mela); the 10-event list in
// .claude/references/eventhub-domain/user-flows.md is stale and doesn't match.
const EVENT_TITLE = 'Dilli Diwali Mela';

// ── Helpers ──────────────────────────────────────────────────────────────────

async function login(page) {
  await page.goto('/login');
  await page.getByPlaceholder('you@email.com').fill(USER_EMAIL);
  await page.getByLabel('Password').fill(USER_PASSWORD);
  await page.locator('#login-btn').click();
  // Home page loads after login — "Browse Events" link confirms successful auth
  // (matches both the nav-bar link and the hero CTA, so scope to the first)
  await expect(page.getByRole('link', { name: /Browse Events/i }).first()).toBeVisible();
}

/**
 * Searches for and opens a specific event's detail page.
 * Precondition: user must be logged in.
 */
async function openEvent(page, title) {
  await page.goto(`/events?search=${encodeURIComponent(title)}`);
  const card = page.getByTestId('event-card').filter({ hasText: title }).first();
  await expect(card).toBeVisible();
  await card.getByTestId('book-now-btn').click();
  await expect(page).toHaveURL(/\/events\/\d+/);
}

async function fillCustomerDetails(page) {
  await page.getByLabel('Full Name').fill('Test User');
  await page.getByTestId('customer-email').fill('testuser@example.com');
  await page.getByLabel('Phone Number').fill('9876543210');
}

/**
 * Reads a label/value row rendered as a flex div containing two <span>s.
 * Fallback for the confirmation/detail "Tickets"/"Total" rows, which don't
 * yet carry data-testid on the deployed app this suite targets (source fix
 * lives at frontend/app/events/[id]/page.tsx and frontend/app/bookings/[id]/page.tsx —
 * switch to getByTestId('confirmation-tickets') / 'confirmation-total' /
 * 'booking-tickets' / 'booking-total-paid' once that build is live).
 */
async function readFieldValue(page, label) {
  const row = page.locator('div').filter({ hasText: label }).last();
  return (await row.locator('span').last().textContent())?.trim();
}

// ── Test Suite ───────────────────────────────────────────────────────────────

test.describe('Booking Flow — Critical E2E Journeys', () => {

  // TC-001 (absorbs TC-002, TC-006) ───────────────────────────────────────────
  test('TC-001: books a single ticket and it appears in My Bookings', async ({ page }) => {
    // -- Step 1: Login --
    await login(page);

    // -- Step 2: Open event and book the default quantity (1 ticket) --
    await openEvent(page, EVENT_TITLE);
    await fillCustomerDetails(page);
    await page.locator('#confirm-booking').click();

    // -- Step 3: Confirmation card shows a booking ref matching the business rule
    //    (first character = event title's first character, uppercase) --
    const refEl = page.locator('.booking-ref');
    await expect(refEl).toBeVisible();
    const bookingRef = (await refEl.textContent())?.trim() ?? '';
    expect(bookingRef.charAt(0)).toBe(EVENT_TITLE.charAt(0).toUpperCase());

    // -- Step 4: Navigate to My Bookings via the confirmation CTA --
    await page.getByRole('button', { name: 'View My Bookings' }).click();
    await expect(page).toHaveURL(/\/bookings$/);

    // -- Step 5: Booking card is visible with matching ref, event title, and status --
    const card = page.getByTestId('booking-card').filter({ hasText: bookingRef });
    await expect(card).toBeVisible();
    await expect(card).toContainText(EVENT_TITLE);
    await expect(card).toContainText('confirmed');
  });

  // TC-007 ─────────────────────────────────────────────────────────────────────
  test('TC-007: booking multiple tickets keeps totals consistent from confirmation to detail page', async ({ page }) => {
    const QUANTITY = 3;

    // -- Step 1: Login --
    await login(page);

    // -- Step 2: Open event and increment the ticket stepper to QUANTITY --
    await openEvent(page, EVENT_TITLE);
    for (let i = 1; i < QUANTITY; i++) {
      await page.getByRole('button', { name: '+' }).click();
    }
    await expect(page.locator('#ticket-count')).toHaveText(String(QUANTITY));

    // -- Step 3: Complete the booking --
    await fillCustomerDetails(page);
    await page.locator('#confirm-booking').click();

    // -- Step 4: Confirmation shows the correct ticket count and total; capture both --
    const refEl = page.locator('.booking-ref');
    await expect(refEl).toBeVisible();
    const bookingRef = (await refEl.textContent())?.trim() ?? '';
    expect(await readFieldValue(page, 'Tickets')).toBe(String(QUANTITY));
    const confirmationTotal = await readFieldValue(page, 'Total');

    // -- Step 5: Navigate to the booking's detail page --
    await page.getByRole('button', { name: 'View My Bookings' }).click();
    const card = page.getByTestId('booking-card').filter({ hasText: bookingRef });
    await card.getByRole('button', { name: 'View Details' }).click();
    await expect(page).toHaveURL(/\/bookings\/\d+/);

    // -- Step 6: Detail page ticket count and total match the confirmation --
    expect(await readFieldValue(page, 'Tickets')).toBe(String(QUANTITY));
    expect(await readFieldValue(page, 'Total Paid')).toBe(confirmationTotal);
  });

  // TC-004 ─────────────────────────────────────────────────────────────────────
  test('TC-004: cancelling a booking from its detail page removes it from the list', async ({ page }) => {
    // -- Step 1: Login and create a booking to cancel --
    await login(page);
    await openEvent(page, EVENT_TITLE);
    await fillCustomerDetails(page);
    await page.locator('#confirm-booking').click();
    const refEl = page.locator('.booking-ref');
    await expect(refEl).toBeVisible();
    const bookingRef = (await refEl.textContent())?.trim() ?? '';

    // -- Step 2: Navigate to the booking's detail page --
    await page.getByRole('button', { name: 'View My Bookings' }).click();
    const card = page.getByTestId('booking-card').filter({ hasText: bookingRef });
    await card.getByRole('button', { name: 'View Details' }).click();
    await expect(page).toHaveURL(/\/bookings\/\d+/);

    // -- Step 3: Cancel via the confirm dialog --
    // NOTE: the detail-page Cancel button doesn't yet carry data-testid="cancel-booking-btn"
    // on the deployed app (source fix in frontend/app/bookings/[id]/page.tsx) — use role/name
    // until that build is live, then switch to getByTestId('cancel-booking-btn').
    await page.getByRole('button', { name: 'Cancel Booking' }).click();
    await page.getByTestId('confirm-dialog-yes').click();

    // -- Step 4: Success toast, redirect to list, booking no longer present --
    await expect(page.getByText('Booking cancelled successfully')).toBeVisible();
    await expect(page).toHaveURL(/\/bookings$/);
    await expect(page.getByTestId('booking-card').filter({ hasText: bookingRef })).toHaveCount(0);
  });
});
