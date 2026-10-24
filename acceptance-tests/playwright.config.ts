import { defineConfig } from '@playwright/test';

/**
 * Playwright Test config for deterministic HTTP API acceptance tests.
 *
 * These tests use the `request` fixture (APIRequestContext) to exercise the
 * fintech-customer-insights REST API over HTTP. They are black-box, deterministic,
 * and run independently of any AI agent.
 *
 * The app base URL is configurable via BASE_URL (defaults to localhost:8080).
 * Tests target a LOCALLY started app only — never a production environment.
 *
 * By default Playwright starts the app itself via the `webServer` block below
 * (`./gradlew bootRun`) and waits until it is reachable before running tests, so
 * the suite is self-contained. Set BASE_URL to point at an already-running app and
 * set PW_NO_WEBSERVER=1 to skip the managed startup (e.g. when the app is started
 * separately). The managed server is never used against a production environment.
 */
const baseURL = process.env.BASE_URL ?? 'http://localhost:8080';
const manageServer = !process.env.PW_NO_WEBSERVER;

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never' }], ['junit', { outputFile: 'test-results/junit.xml' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    extraHTTPHeaders: {
      Accept: 'application/json',
    },
  },
  ...(manageServer
    ? {
        webServer: {
          command: '../fintech-customer-insights/gradlew -p ../fintech-customer-insights bootRun',
          url: `${baseURL}/v3/api-docs`,
          reuseExistingServer: !process.env.CI,
          timeout: 180_000,
          stdout: 'pipe',
          stderr: 'pipe',
        },
      }
    : {}),
});
