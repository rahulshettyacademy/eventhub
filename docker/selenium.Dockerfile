# ══════════════════════════════════════════════════════════════════════════════
# Selenium WebDriver E2E tests (selenium/, Core Java + TestNG + Maven)
# — no Java, Maven or Chrome needed on the host
#
# Build context: repository root (see docker-compose.yml)
# Run:           docker compose run --rm --build selenium
# Reports:       reports/selenium-report/test-case-report.html (plus TestNG's index.html, emailable-report.html)
# ══════════════════════════════════════════════════════════════════════════════

# Maven 3.9 + Java 21 (Eclipse Temurin), Ubuntu based
FROM maven:3.9.9-eclipse-temurin-21

# Google Chrome stable (amd64 only; docker-compose.yml pins the platform).
# apt resolves Chrome's own library dependencies from the .deb.
RUN apt-get update \
 && apt-get install -y --no-install-recommends wget ca-certificates \
 && wget -q -O /tmp/chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
 && apt-get install -y --no-install-recommends /tmp/chrome.deb \
 && rm -f /tmp/chrome.deb \
 && rm -rf /var/lib/apt/lists/* \
 && google-chrome --version

WORKDIR /app/selenium

# Dependencies first, so this layer is cached until pom.xml changes
COPY selenium/pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY selenium/testng.xml ./
COPY selenium/src/ ./src/
RUN mvn -B -q test-compile

# Chrome runs as root in the container, which requires --no-sandbox (DriverFactory reads this)
ENV CHROME_NO_SANDBOX=true

# Run every class in testng.xml (headless Chrome), copy the TestNG HTML report and
# any failure screenshots to /reports/selenium-report, and keep Maven's exit code.
CMD ["sh", "-c", "mvn -B test; rc=$?; rm -rf /reports/selenium-report; mkdir -p /reports/selenium-report; cp -r target/surefire-reports/. /reports/selenium-report/ 2>/dev/null; if [ -d target/screenshots ]; then cp -r target/screenshots /reports/selenium-report/; fi; exit $rc"]
