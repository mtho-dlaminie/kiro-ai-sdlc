import { test, expect } from '@playwright/test';

/**
 * Baseline smoke acceptance tests for the existing fintech-customer-insights endpoints.
 *
 * These validate the BASELINE behaviour (profile, accounts, transactions, credit-status,
 * financial-overview). Feature-specific suites (e.g. financial-health) are added later by
 * the api-verifier agent through the feature-delivery workflow.
 *
 * Deterministic synthetic fixtures (see BASELINE-REPORT.md):
 *   CUST-000001 full data · CUST-000003 empty collections · CUST-999999 unknown · bad id 400
 */

const KNOWN = 'CUST-000001';
const EMPTY = 'CUST-000003';
const UNKNOWN = 'CUST-999999';
const MALFORMED = 'not-a-valid-id';

test.describe('customer profile', () => {
  test('known customer returns 200 JSON with identifiers', async ({ request }) => {
    const res = await request.get(`/api/v1/customers/${KNOWN}/profile`);
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('application/json');
    const body = await res.json();
    expect(body).toHaveProperty('customerId', KNOWN);
  });

  test('unknown customer returns 404 problem+json', async ({ request }) => {
    const res = await request.get(`/api/v1/customers/${UNKNOWN}/profile`);
    expect(res.status()).toBe(404);
    expect(res.headers()['content-type']).toContain('application/problem+json');
    const body = await res.json();
    expect(body).toHaveProperty('status', 404);
  });

  test('malformed customerId returns 400', async ({ request }) => {
    const res = await request.get(`/api/v1/customers/${MALFORMED}/profile`);
    expect(res.status()).toBe(400);
    expect(res.headers()['content-type']).toContain('application/problem+json');
  });
});

test.describe('accounts', () => {
  test('known customer returns an array of accounts', async ({ request }) => {
    const res = await request.get(`/api/v1/customers/${KNOWN}/accounts`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    const items = Array.isArray(body) ? body : body.items ?? body.accounts;
    expect(Array.isArray(items)).toBeTruthy();
    expect(items.length).toBeGreaterThan(0);
  });

  test('empty-collections customer returns 200 with no accounts (never 404)', async ({ request }) => {
    const res = await request.get(`/api/v1/customers/${EMPTY}/accounts`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    const items = Array.isArray(body) ? body : body.items ?? body.accounts;
    expect(Array.isArray(items)).toBeTruthy();
    expect(items.length).toBe(0);
  });
});

test.describe('transactions date range', () => {
  test('from > to returns 400', async ({ request }) => {
    const res = await request.get(
      `/api/v1/customers/${KNOWN}/transactions?from=2026-12-31&to=2026-01-01`,
    );
    expect(res.status()).toBe(400);
  });
});

test.describe('financial-overview aggregator', () => {
  test('known customer returns 200 aggregated view', async ({ request }) => {
    const res = await request.get(`/api/v1/customers/${KNOWN}/financial-overview`);
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('application/json');
  });
});

test.describe('OpenAPI', () => {
  // Reconciled (AC-17): the financial-health endpoint now legitimately exists, so the baseline
  // smoke no longer asserts its absence. Its presence is covered by financial-health.spec.ts.
  test('/v3/api-docs is served in the baseline', async ({ request }) => {
    const res = await request.get('/v3/api-docs');
    expect(res.status()).toBe(200);
    const text = await res.text();
    expect(text).toContain('/api/v1/customers/{customerId}/financial-overview');
  });
});
