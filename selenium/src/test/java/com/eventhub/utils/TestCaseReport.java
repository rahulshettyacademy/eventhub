package com.eventhub.utils;

import org.testng.IReporter;
import org.testng.ISuite;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.xml.XmlSuite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes test-case-report.html next to TestNG's own reports: one row per test case, sorted by TC number,
 * with its status, duration and failure reason. Registered in testng.xml.
 */
public class TestCaseReport implements IReporter {
    private static final String FILE_NAME = "test-case-report.html";
    /** "TC12: ...", "TC-200: ..." or "TC-006 + TC-512: ..."; the first number sets the sort order. */
    private static final Pattern TC_LABEL = Pattern.compile("^(TC-?(\\d+)(?:\\s*\\+\\s*TC-?\\d+)*):\\s*(.*)$");

    private record Row(String id, int number, String scenario, String testClass, String status, long millis, String failure) {
    }

    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectory) {
        List<Row> rows = new ArrayList<>();
        for (ISuite suite : suites) {
            for (ISuiteResult suiteResult : suite.getResults().values()) {
                ITestContext context = suiteResult.getTestContext();
                context.getPassedTests().getAllResults().forEach(r -> rows.add(toRow(r, "PASS")));
                context.getFailedTests().getAllResults().forEach(r -> rows.add(toRow(r, "FAIL")));
                context.getSkippedTests().getAllResults().forEach(r -> rows.add(toRow(r, "SKIP")));
            }
        }
        rows.sort(Comparator.comparingInt(Row::number).thenComparing(Row::id));

        try {
            Path file = Path.of(outputDirectory, FILE_NAME);
            Files.createDirectories(file.getParent());
            String title = suites.isEmpty() ? "Test" : escape(suites.get(0).getName());
            Files.writeString(file, html(title, rows), StandardCharsets.UTF_8);
            System.out.println("Test case report: " + file.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The label comes from a data-provider case's toString() ("TC12: no webAutomation") or the @Test description. */
    private static Row toRow(ITestResult result, String status) {
        Object[] params = result.getParameters();
        String label = params.length > 0 ? String.valueOf(params[0]) : result.getMethod().getDescription();
        if (label == null) label = result.getMethod().getMethodName();

        Matcher tc = TC_LABEL.matcher(label);
        String id = tc.matches() ? tc.group(1) : "-";
        int number = tc.matches() ? Integer.parseInt(tc.group(2)) : Integer.MAX_VALUE;
        String scenario = tc.matches() ? tc.group(3) : label;

        Throwable error = result.getThrowable();
        String failure = error == null ? "" : String.valueOf(error.getMessage());
        return new Row(id, number, scenario, result.getTestClass().getRealClass().getSimpleName(), status,
                result.getEndMillis() - result.getStartMillis(), failure);
    }

    private static String html(String title, List<Row> rows) {
        long passed = rows.stream().filter(r -> r.status().equals("PASS")).count();
        long failed = rows.stream().filter(r -> r.status().equals("FAIL")).count();
        long skipped = rows.stream().filter(r -> r.status().equals("SKIP")).count();

        StringBuilder body = new StringBuilder();
        for (Row row : rows) {
            body.append("<tr class=\"").append(row.status().toLowerCase()).append("\">")
                    .append("<td>").append(escape(row.id())).append("</td>")
                    .append("<td>").append(escape(row.scenario())).append("</td>")
                    .append("<td>").append(escape(row.testClass())).append("</td>")
                    .append("<td><span class=\"badge\">").append(row.status()).append("</span></td>")
                    .append("<td class=\"num\">").append(row.millis()).append(" ms</td>")
                    .append("<td class=\"failure\">").append(escape(row.failure())).append("</td>")
                    .append("</tr>\n");
        }

        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s – Test Cases</title>
                <style>
                  body { font-family: system-ui, sans-serif; margin: 24px; color: #1f2328; background: #fff; }
                  h1 { font-size: 20px; margin: 0 0 4px; }
                  .meta { color: #59636e; margin-bottom: 16px; }
                  .summary span { display: inline-block; padding: 6px 12px; border-radius: 6px; margin-right: 8px; font-weight: 600; }
                  .s-total { background: #eef1f4; } .s-pass { background: #dafbe1; color: #116329; }
                  .s-fail { background: #ffebe9; color: #a40e26; } .s-skip { background: #fff8c5; color: #7d4e00; }
                  table { border-collapse: collapse; width: 100%%; margin-top: 16px; font-size: 14px; }
                  th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #d1d9e0; vertical-align: top; }
                  th { background: #f6f8fa; position: sticky; top: 0; }
                  .num { text-align: right; white-space: nowrap; }
                  .badge { padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 600; }
                  tr.pass .badge { background: #dafbe1; color: #116329; }
                  tr.fail .badge { background: #ffebe9; color: #a40e26; }
                  tr.skip .badge { background: #fff8c5; color: #7d4e00; }
                  tr.fail { background: #fff5f5; }
                  .failure { font-family: ui-monospace, monospace; font-size: 12px; color: #a40e26; white-space: pre-wrap; }
                </style>
                </head>
                <body>
                <h1>%s – test case results</h1>
                <div class="meta">Generated %s</div>
                <div class="summary">
                  <span class="s-total">Total: %d</span><span class="s-pass">Passed: %d</span><span class="s-fail">Failed: %d</span><span class="s-skip">Skipped: %d</span>
                </div>
                <table>
                <thead><tr><th>ID</th><th>Scenario</th><th>Class</th><th>Status</th><th>Time</th><th>Failure reason</th></tr></thead>
                <tbody>
                %s</tbody>
                </table>
                </body>
                </html>
                """.formatted(title, title, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                rows.size(), passed, failed, skipped, body);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
