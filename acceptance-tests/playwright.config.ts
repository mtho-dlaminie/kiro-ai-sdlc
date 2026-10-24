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
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never' }], ['junit', { outputFile: 'test-results/junit.xml' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:8080',
    extraHTTPHeaders: {
      Accept: 'application/json',
    },
  },
});
