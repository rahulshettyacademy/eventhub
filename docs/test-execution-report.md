# EventHub — Test Execution Report

**Run date:** 2026-10-26 (latest full run)
**Environment:** https://eventhub.rahulshettyacademy.com (API: https://api.eventhub.rahulshettyacademy.com/api)
**Browser:** Google Chrome 155.0.8059.85, headed (real browser window), for both suites
**Account:** `rahulshetty1@gmail.com` (shared live account, so the suites run one after another, never in parallel)

## Summary

| Suite | Framework | Tests | Passed | Failed | Skipped | Duration |
|---|---|---|---|---|---|---|
| All specs in `tests/` (5 files) | Playwright 1.58.2 (Node 24.22.0) | 12 | 12 | 0 | 0 | 2.3 min |
| `selenium/` — `BookingFlowTest` | Selenium 4 + TestNG (Java 25, Maven 3.9.9) | 3 | 3 | 0 | 0 | ~8.2 s (test bodies) |
| **Total** | | **15** | **15** | **0** | **0** | |

**Result: PASS**

## Playwright

`playwright.config.ts` targets Playwright's bundled Chromium in headless mode. For this run, a temporary local config reused it with `channel: 'chrome'` and `headless: false`, so the suite ran in the installed Google Chrome with a visible window. The project config was not changed.

Equivalent command with the project config (bundled Chromium, headless):
```bash
npx playwright test
```

| ID | Test | Spec | Result |
|---|---|---|---|
| TC-001 | Bookings list shows a card with the booking details | booking-flow.spec.js | ✅ Pass |
| TC-002 | Booking detail page shows all sections | booking-flow.spec.js | ✅ Pass |
| TC-003 | Cancelling from the detail page shows a toast, redirects and deletes the booking | booking-flow.spec.js | ✅ Pass |
| TC-004 | Cancelling from a list card removes only that card and stays on the list | booking-flow.spec.js | ✅ Pass |
| TC-005 | Clear all bookings empties the list after confirming | booking-flow.spec.js | ✅ Pass |
| TC-006 | Booking through the UI then "View My Bookings" lists the new booking first | booking-flow.spec.js | ✅ Pass |
| TC-107 | Booking reference starts with the first letter of the event title (uppercase) | booking-management.spec.js | ✅ Pass |
| — | Gmail user sees Access Denied when viewing a Yahoo user's booking | cross-user-booking.spec.js | ✅ Pass |
| — | Refund eligible for a single-ticket booking | refund-eligibility.spec.js | ✅ Pass |
| — | Refund not eligible for a group-ticket booking | refund-eligibility.spec.js | ✅ Pass |
| — | Sandbox banner is shown when 6 events are returned (mocked API) | sandbox-banner.spec.js | ✅ Pass |
| — | Sandbox banner is hidden when 4 events are returned (mocked API) | sandbox-banner.spec.js | ✅ Pass |

Observed in this run: all test data was booked on "Dilli Diwali Mela". TC-107 got ref `D-RHHRQ5`, which satisfies the booking-ref rule. TC-006 booked `D-N73OP7` through the UI. The cross-user test tried to open the Yahoo user's booking #40537 as the Gmail user and got "Access Denied".

HTML report: `playwright-report/index.html` (`npx playwright show-report`)

## Selenium (Java)

Command (headed Chrome):
```bash
mvn -f selenium/pom.xml clean test -Dheaded=true
```

| ID | Test method | Result | Duration |
|---|---|---|---|
| TC-001 | `tc001_bookingsListShowsCard`: bookings list shows a card with the booking details | ✅ Pass | 1.9 s |
| TC-002 | `tc002_detailPageShowsAllSections`: booking detail page shows all sections | ✅ Pass | 3.2 s |
| TC-003 | `tc003_cancelFromDetailPage`: cancelling from the detail page shows a toast, redirects and deletes the booking | ✅ Pass | 3.1 s |

The suite also passed 3/3 in headless Chrome (the default, without `-Dheaded=true`) in the same session.

Reports: `selenium/target/surefire-reports/index.html`, `emailable-report.html`, `testng-results.xml`

Non-blocking warnings: Selenium printed `Unable to find CDP implementation matching 155`. That only affects DevTools features, which these tests don't use. To silence it, add the matching `selenium-devtools` dependency or upgrade Selenium.

## Notes on the restored specs

`cross-user-booking.spec.js`, `refund-eligibility.spec.js` and `sandbox-banner.spec.js` were recovered from older branches. They had been written for `http://localhost:3000`, and their `BASE_URL` now points at the live site like the other specs.

## Coverage vs. scenarios

`docs/test-strategy.md` §2.4 assigns 10 scenarios to the E2E layer. These suites cover the six booking-journey ones (TC-001 to TC-006). Playwright covers all six plus TC-107 (booking-ref prefix, which also has unit and API layers in the strategy). Selenium covers TC-001 to TC-003.

`cross-user-booking.spec.js` checks the cross-user "Access Denied" rule (the strategy's TC-200 journey) end to end. Still not covered: TC-512 (list/seat refresh) has no dedicated test.

## Test data cleanup

- **Booking-flow, booking-management and Selenium tests:** each creates its own bookings through the API and deletes them afterwards. TC-005 clears all of the account's bookings by design.
- **Cross-user and refund tests:** they book through the UI and don't delete their bookings. The live account's FIFO limit of 9 bookings keeps that bounded.
