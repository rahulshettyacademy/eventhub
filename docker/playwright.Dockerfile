# ══════════════════════════════════════════════════════════════════════════════
# Playwright E2E tests (tests/*.spec.js) — no Node or browsers needed on the host
#
# Build context: repository root (see docker-compose.yml)
# Run:           docker compose run --rm --build playwright
# Reports:       reports/playwright-report/ (HTML), reports/playwright-test-results/
# ══════════════════════════════════════════════════════════════════════════════

# Official Playwright image: Node.js and Chromium preinstalled. Keep the tag in
# sync with @playwright/test in package-lock.json.
FROM mcr.microsoft.com/playwright:v1.58.2-noble

WORKDIR /app

# Dependencies first, so this layer is cached until package*.json changes.
# --ignore-scripts: the browsers already ship with the image.
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts

COPY playwright.config.ts ./
COPY tests/ ./tests/

# Write reports under /reports, which docker-compose.yml mounts from ./reports
ENV PLAYWRIGHT_HTML_OUTPUT_DIR=/reports/playwright-report \
    PLAYWRIGHT_HTML_OPEN=never \
    CI=true

# Line output in the console, HTML report in /reports/playwright-report
CMD ["npx", "playwright", "test", "--reporter=line,html", "--output=/reports/playwright-test-results"]
