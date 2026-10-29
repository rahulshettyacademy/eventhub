import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: false,
  // All specs share one live account (and TC-005 clears all its bookings),
  // so spec files must not run at the same time either
  workers: 1,
  retries: 0,
  reporter: 'html',

  use: {
    baseURL: 'https://eventhub.rahulshettyacademy.com',
    headless: true,
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
