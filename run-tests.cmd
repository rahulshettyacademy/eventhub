@echo off
rem Runs the EventHub E2E suites in Docker: Playwright and Selenium in parallel.
rem Only Docker Desktop is required on this machine. Reports are written to .\reports.
rem
rem Usage:  run-tests.cmd              both suites, in parallel
rem         run-tests.cmd playwright   one suite
rem         run-tests.cmd selenium
rem
rem The suites log in as different users (Playwright: rahulshetty1@gmail.com / @yahoo.com,
rem Selenium: its own accounts, see selenium\...\config\Config.java), and the app keeps
rem bookings and seat counts per user, so they don't interfere. Output lines are prefixed
rem with the service name. The script exits with 1 if either suite failed.

setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "SUITE=%~1"
if "%SUITE%"=="" set "SUITE=all"
if /i "%SUITE%"=="all" (
  set "SERVICES=playwright selenium"
) else if /i "%SUITE%"=="playwright" (
  set "SERVICES=playwright"
) else if /i "%SUITE%"=="selenium" (
  set "SERVICES=selenium"
) else (
  echo Usage: %~nx0 [all^|playwright^|selenium]
  exit /b 2
)

if not exist reports mkdir reports

rem Build the images, then start the suites together and wait until every container has exited
docker compose up --build %SERVICES%

echo.
echo === Summary ===
set "FAILED=0"
for %%s in (%SERVICES%) do (
  rem "Not run" counts as a failure, e.g. the build failed
  set "RC=1"
  for /f %%i in ('docker compose ps -a -q %%s') do (
    for /f %%c in ('docker inspect -f "{{.State.ExitCode}}" %%i') do set "RC=%%c"
  )
  if "!RC!"=="0" (echo %%s: PASSED) else (echo %%s: FAILED ^(exit !RC!^) & set "FAILED=1")
  rem Playwright's index.html lists every test; for Selenium, TestNG's index.html hides them, so point at our table
  if "%%s"=="selenium" (echo   report: reports\%%s-report\test-case-report.html) else (echo   report: reports\%%s-report\index.html)
)

docker compose down --remove-orphans >nul 2>&1
exit /b %FAILED%
