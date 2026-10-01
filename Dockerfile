# Node 20 + Playwright with all browsers (Chromium, Firefox, WebKit) pre-installed.
# The Playwright version is taken from package-lock.json, so browsers always match it.
FROM node:20-bookworm

WORKDIR /app

# Install only root (Playwright) dependencies
COPY package.json package-lock.json ./
RUN npm ci

# Install all browsers plus their OS-level dependencies
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN npx playwright install --with-deps chromium\
    && rm -rf /var/lib/apt/lists/*
COPY playwright.config.ts ./
COPY tests ./tests

ENV CI=true \
    PLAYWRIGHT_HTML_OPEN=never

CMD ["npx", "playwright", "test"]
