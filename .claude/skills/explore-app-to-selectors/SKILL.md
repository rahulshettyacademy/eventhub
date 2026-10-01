---
name: explore-app-to-selectors
description: Explore the live EventHub app on http://localhost:3000 with the Playwright MCP and produce a verified selector and behaviour map for a page or flow. Use this whenever the user wants to explore, walk through, inspect, or verify the real UI, or before writing or fixing Playwright tests for any page or flow (login, events, booking, bookings list, booking detail, refund, create event), even if they never say "explore". Also use when a test fails on a locator, or when ui-selectors.md may be stale.
---

# Explore App to Selectors

Docs in `.claude/references/eventhub-domain/` drift from the real UI. Tests written from stale docs fail on locators. This skill walks the running app first and records what is actually there, so `/generate-tests` starts from verified facts.

## Inputs

A feature or flow name, e.g. "booking a ticket", "refund check", "create event". If none is given, ask which flow.

## Steps

1. **Check the app is up.** Base URL is `http://localhost:3000` (backend on 3001). Run `curl -s -o /dev/null -w "%{http_code}" http://localhost:3000`. If it isn't 200, tell the user to run `npm run dev` (and `npm run seed` if there are no events) and stop; don't start long-running servers yourself unless asked.

2. **Read the docs first** so you know what to compare against: `.claude/references/eventhub-domain/ui-selectors.md`, `business-rules.md`, `user-flows.md`. Then skim the relevant `frontend/app/**` and `frontend/components/**` source, because `data-testid` attributes are visible there and current uncommitted edits show up there before anywhere else.

3. **Walk the flow with the Playwright MCP.** Log in as `rahulshetty1@gmail.com` / `Magiclife1!` (use the Yahoo user only for cross-user checks). At each page use `browser_snapshot` (accessibility tree) rather than screenshots to read roles, names and labels. Do the real actions of the flow, including a state change where the flow has one (book, cancel, create). Use unique data like `Explore Event ${Date.now()}`. Remember the sandbox limits: max 6 created events, max 9 bookings, with FIFO pruning.

4. **Record a locator for each element that a test would touch**, using the project priority: `data-testid` > role > label/placeholder > `#id` > CSS class. For each, confirm it resolves to exactly one element (or the intended set) via `browser_find` or `browser_evaluate`. Never record XPath or nth-child chains.

5. **Check behaviour against business rules** while you're there, and note pass/fail with the observed value: booking ref first char equals event title first char (uppercase); seats drop on booking and are restored on cancel; refund is eligible only for 1 ticket; static events can't be edited; cross-user access returns "Access Denied".

6. **Report**, in this shape:

```
## Flow: <name>  (explored <date>, http://localhost:3000)
### Steps
1. <page> - <action> -> <observed result>
### Verified locators
| Element | Locator | Priority tier | Matches |
### Drift from docs
- ui-selectors.md says X, real UI has Y (file to update)
### Missing data-testid (suggested)
- <element>: suggest data-testid="..." in <frontend file>
### Business rule checks
- <rule>: PASS/FAIL (observed ...)
### Timing/quirks
- e.g. spinner duration, async toasts
```

7. **Offer, don't force, follow-ups**: updating `ui-selectors.md` with the drift, and running `/generate-tests <flow>` with this map. Edit docs or frontend files only if the user says yes.

## Why these choices

- Snapshots over screenshots: the accessibility tree is exactly what `getByRole`/`getByLabel` match against.
- Uniqueness check: a locator that matches 3 elements passes exploration but flakes in strict-mode tests.
- Reporting drift rather than silently fixing it keeps the user in control of the shared reference docs.
