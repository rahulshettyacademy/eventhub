#!/usr/bin/env bash
# Runs the EventHub E2E suites in Docker: Playwright and Selenium in parallel.
# Only Docker is required on this machine. Reports are written to ./reports.
#
# Usage:  ./run-tests.sh              # both suites, in parallel
#         ./run-tests.sh playwright   # one suite
#         ./run-tests.sh selenium
#
# The suites log in as different users (Playwright: rahulshetty1@gmail.com / @yahoo.com,
# Selenium: its own accounts, see selenium/.../config/Config.java), and the app keeps
# bookings and seat counts per user, so they don't interfere. Output lines are prefixed
# with the service name. The script exits non-zero if either suite failed.

set -u
cd "$(dirname "$0")"

suite="${1:-all}"
case "$suite" in
  all)                 services="playwright selenium" ;;
  playwright|selenium) services="$suite" ;;
  *) echo "Usage: $0 [all|playwright|selenium]" >&2; exit 2 ;;
esac

mkdir -p reports

# Build the images, then start the suites together and wait until every container has exited
docker compose up --build $services

# Exit code of a suite's container ("not run" counts as a failure, e.g. the build failed)
exit_code() {
  local id
  id=$(docker compose ps -a -q "$1")
  [ -n "$id" ] && docker inspect -f '{{.State.ExitCode}}' "$id" || echo 1
}

failed=0
echo
echo "=== Summary ==="
for service in $services; do
  rc=$(exit_code "$service")
  [ "$rc" -eq 0 ] && status="PASSED" || { status="FAILED (exit $rc)"; failed=1; }
  printf '%-11s %s\n' "$service:" "$status"
  # Playwright's index.html lists every test; for Selenium, TestNG's index.html hides them, so point at our table
  report=index.html
  [ "$service" = selenium ] && report=test-case-report.html
  echo "  report: reports/$service-report/$report"
done

docker compose down --remove-orphans >/dev/null 2>&1
exit $failed
