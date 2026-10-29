import { test, expect } from '@playwright/test';

const BASE_URL   = 'https://eventhub.rahulshettyacademy.com';
const YAHOO_USER = { email: 'rahulshetty1@yahoo.com', password: 'Magiclife1!' };
const GMAIL_USER = { email: 'rahulshetty1@gmail.com', password: 'Magiclife1!' };

async function loginAs(page, user) {
    await page.goto(`${BASE_URL}/login`);
    await page.getByPlaceholder('you@email.com').fill(user.email);
    await page.getByLabel('Password').fill(user.password);
    await page.locator('#login-btn').click();
    await expect(page.getByRole('link', { name: 'Browse Events →' })).toBeVisible();
}

test('gmail user sees Access Denied when viewing yahoo user booking', async ({ page }) => {

    // ── Step 1: Login as Yahoo user and create a booking via browser ──────────
    await loginAs(page, YAHOO_USER);

    await page.goto(`${BASE_URL}/events`);
    await page.getByTestId('event-card').first().getByTestId('book-now-btn').click();
    await expect(page).toHaveURL(/\/events\/\d+/);

    await page.getByLabel('Full Name').fill('Yahoo User');
    await page.locator('#customer-email').fill(YAHOO_USER.email);
    await page.getByPlaceholder('+91 98765 43210').fill('9999999999');
    await page.locator('.confirm-booking-btn').click();

    // Navigate to bookings and capture the booking ID from the link href
    await page.getByRole('link', { name: 'View My Bookings' }).click();
    await expect(page).toHaveURL(`${BASE_URL}/bookings`);

    const detailsLink = page.getByRole('link', { name: 'View Details' }).first();
    const yahooBookingId = (await detailsLink.getAttribute('href')).split('/').pop();
    await detailsLink.click();
    await expect(page.getByText('Booking Information')).toBeVisible();
    console.log(`Yahoo booking ID to cross-access: ${yahooBookingId}`);

    // ── Step 2: Switch to Gmail user ──────────────────────────────────────────
    await page.evaluate(() => localStorage.removeItem('eventhub_token'));
    await loginAs(page, GMAIL_USER);

    // ── Step 3: Navigate directly to Yahoo's booking with Gmail token ─────────
    await page.goto(`${BASE_URL}/bookings/${yahooBookingId}`, { waitUntil: 'networkidle' });

    // ── Step 4: Validate Access Denied message ────────────────────────────────
    await expect(page.getByText('Access Denied')).toBeVisible();
    await expect(page.getByText('You are not authorized to view this booking')).toBeVisible();
});
