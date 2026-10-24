import { test, expect } from '@playwright/test';

/**
 * Black-box HTTP API acceptance tests for
 *   GET /api/v1/customers/{customerId}/financial-health
 *
 * Deterministic Playwright Test (`request` / APIRequestContext) tests, run against a LOCALLY
 * started app only — never a real/production environment (testing-standards, api-verifier rules).
 *
 * Derived from the approved spec v2 acceptance criteria:
 *   AC-1  known customer → 200 application/json, documented shape
 *   AC-3  derived monetary + ratio fields with correct scale (business values)
 *   AC-6  deterministic 0-100 score + categorical rating (POOR/FAIR/GOOD/EXCELLENT)
 *   AC-8  ordered recommendations, cap 3, non-empty fallback
 *   AC-11 unknown customer (CUST-999999) → 404 application/problem+json (RFC 7807)
 *   AC-12 malformed id → 400 application/problem+json (RFC 7807)
 *   AC-13 dependency failure (CUST-000004) → 503 application/problem+json
 *   AC-15 empty-data customer (CUST-000003) → 200, never 404
 *   AC-17 /v3/api-docs documents the financial-health path
 *   AC-19 every 200 body carries the synthetic-data disclaimer
 *
 * Fixtures (deterministic synthetic seed — fintech-customer-insights/.../FixtureData.java):
 *   CUST-000001  Ada / GBP   full data (2 credits, 1 debit, debt 1200.00, util 18.50%)
 *   CUST-000003  Grace / EUR empty accounts + transactions, zero debt/util
 *   CUST-000004  reserved id → deterministic dependency-failure seam (503)
 *   CUST-999999  reserved well-formed id, never present (404)
 *
 * Monetary scale note: the API serializes BigDecimal with an explicit scale on the wire
 * (e.g. `"monthlyExpenses":45.20`). JavaScript's JSON.parse normalizes numbers to IEEE doubles
 * and drops trailing zeros, so scale is asserted against the raw response text, while numeric
 * business values are asserted after parsing. This checks both the contract and the arithmetic.
 */

const FULL = 'CUST-000001';
const EMPTY = 'CUST-000003';
const DEP_FAIL = 'CUST-000004';
const UNKNOWN = 'CUST-999999';
const MALFORMED = 'not-a-valid-id';

const RATINGS = ['POOR', 'FAIR', 'GOOD', 'EXCELLENT'];

function path(id: string): string {
  return `/api/v1/customers/${id}/financial-health`;
}

/** Assert that a numeric field appears in the raw JSON with the exact given literal (preserves scale). */
function expectRawNumberLiteral(rawBody: string, field: string, literal: string): void {
  expect(rawBody).toContain(`"${field}":${literal}`);
}

test.describe('financial-health · success (AC-1, AC-3, AC-6, AC-8, AC-19)', () => {
  test('full-data customer returns 200 application/json with the documented shape', async ({ request }) => {
    const res = await request.get(path(FULL));
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('application/json');

    const body = await res.json();
    expect(body).toMatchObject({ customerId: FULL, currency: 'GBP' });
    expect(typeof body.financialHealthScore).toBe('number');
    expect(typeof body.rating).toBe('string');
    expect(Array.isArray(body.recommendations)).toBeTruthy();
    expect(body).toHaveProperty('monthlyIncome');
    expect(body).toHaveProperty('monthlyExpenses');
    expect(body).toHaveProperty('savingsRate');
    expect(body).toHaveProperty('debtToIncomeRatio');
    expect(body).toHaveProperty('disclaimer');
  });

  test('full-data customer exposes the exact deterministic derived values (AC-3, AC-6, AC-8)', async ({ request }) => {
    const res = await request.get(path(FULL));
    expect(res.status()).toBe(200);
    const raw = await res.text();
    const body = JSON.parse(raw);

    // Exact on-the-wire scale (money scale 2, ratios scale 4).
    expectRawNumberLiteral(raw, 'monthlyIncome', '2750.75');
    expectRawNumberLiteral(raw, 'monthlyExpenses', '45.20');
    expectRawNumberLiteral(raw, 'savingsRate', '0.9836');
    expectRawNumberLiteral(raw, 'debtToIncomeRatio', '0.4362');

    // Numeric business values (income = 2500.00 + 250.75; expenses = |−45.20|).
    expect(body.monthlyIncome).toBeCloseTo(2750.75, 2);
    expect(body.monthlyExpenses).toBeCloseTo(45.2, 2);
    // savingsRate = (2750.75 − 45.20) / 2750.75 ; dti = 1200.00 / 2750.75
    expect(body.savingsRate).toBeCloseTo(0.9836, 4);
    expect(body.debtToIncomeRatio).toBeCloseTo(0.4362, 4);

    // score = 82 → EXCELLENT (>= 80 band)
    expect(body.financialHealthScore).toBe(82);
    expect(body.rating).toBe('EXCELLENT');

    // dti 0.4362 > 0.4000 → exactly one recommendation (debt), cap 3
    expect(body.recommendations.length).toBe(1);
    expect(String(body.recommendations[0]).toLowerCase()).toContain('debt-to-income');
  });

  test('score is within 0..100 and rating is a known band (AC-6 boundary)', async ({ request }) => {
    const res = await request.get(path(FULL));
    const body = await res.json();
    expect(body.financialHealthScore).toBeGreaterThanOrEqual(0);
    expect(body.financialHealthScore).toBeLessThanOrEqual(100);
    expect(RATINGS).toContain(body.rating);
  });

  test('recommendations list is capped at 3 and never empty (AC-8 boundary)', async ({ request }) => {
    const res = await request.get(path(FULL));
    const body = await res.json();
    expect(body.recommendations.length).toBeGreaterThanOrEqual(1);
    expect(body.recommendations.length).toBeLessThanOrEqual(3);
    for (const rec of body.recommendations) {
      expect(typeof rec).toBe('string');
      expect(rec.length).toBeGreaterThan(0);
    }
  });

  test('every 200 body carries the synthetic-data disclaimer (AC-19)', async ({ request }) => {
    const res = await request.get(path(FULL));
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(typeof body.disclaimer).toBe('string');
    expect(String(body.disclaimer).toLowerCase()).toContain('synthetic');
    expect(String(body.disclaimer).toLowerCase()).toContain('does not represent real');
  });
});

test.describe('financial-health · empty data (AC-15)', () => {
  test('empty-collections customer returns 200, never 404', async ({ request }) => {
    const res = await request.get(path(EMPTY));
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('application/json');

    const raw = await res.text();
    const body = JSON.parse(raw);
    expect(body).toMatchObject({ customerId: EMPTY, currency: 'EUR' });

    // No transactions → zero income/expenses; zero debt → defined zero DTI, serialized with scale.
    expectRawNumberLiteral(raw, 'monthlyIncome', '0.00');
    expectRawNumberLiteral(raw, 'monthlyExpenses', '0.00');
    expectRawNumberLiteral(raw, 'savingsRate', '0.0000');
    expectRawNumberLiteral(raw, 'debtToIncomeRatio', '0.0000');
    expect(body.monthlyIncome).toBe(0);
    expect(body.monthlyExpenses).toBe(0);

    // Still produces a valid in-range score, rating and a non-empty recommendation set.
    expect(body.financialHealthScore).toBeGreaterThanOrEqual(0);
    expect(body.financialHealthScore).toBeLessThanOrEqual(100);
    expect(RATINGS).toContain(body.rating);
    expect(body.recommendations.length).toBeGreaterThanOrEqual(1);
    expect(String(body.disclaimer).toLowerCase()).toContain('synthetic');
  });
});

test.describe('financial-health · errors (AC-11, AC-12, AC-13)', () => {
  test('unknown customer returns 404 RFC 7807 problem+json (AC-11)', async ({ request }) => {
    const res = await request.get(path(UNKNOWN));
    expect(res.status()).toBe(404);
    expect(res.headers()['content-type']).toContain('application/problem+json');

    const body = await res.json();
    expect(body).toHaveProperty('status', 404);
    expect(body).toHaveProperty('title');
    expect(body).toHaveProperty('detail');
    // RFC 7807 members present; must not leak a stack trace.
    expect(body).toHaveProperty('type');
    expect(JSON.stringify(body)).not.toMatch(/at com\.example\.fintech|Exception:/);
  });

  test('malformed customerId returns 400 RFC 7807 problem+json (AC-12)', async ({ request }) => {
    const res = await request.get(path(MALFORMED));
    expect(res.status()).toBe(400);
    expect(res.headers()['content-type']).toContain('application/problem+json');

    const body = await res.json();
    expect(body).toHaveProperty('status', 400);
    expect(body).toHaveProperty('title');
    expect(JSON.stringify(body)).not.toMatch(/at com\.example\.fintech|Exception:/);
  });

  test('empty path segment (missing id) does not 200 as financial-health', async ({ request }) => {
    const res = await request.get('/api/v1/customers//financial-health');
    expect([400, 404]).toContain(res.status());
  });

  test('dependency failure returns 503 problem+json (AC-13)', async ({ request }) => {
    const res = await request.get(path(DEP_FAIL));
    expect(res.status()).toBe(503);
    expect(res.headers()['content-type']).toContain('application/problem+json');

    const body = await res.json();
    expect(body).toHaveProperty('status', 503);
    expect(body).toHaveProperty('title');
    expect(JSON.stringify(body)).not.toMatch(/at com\.example\.fintech/);
  });

  test('only GET is mapped — POST is rejected (405)', async ({ request }) => {
    const res = await request.post(path(FULL));
    expect(res.status()).toBe(405);
  });
});

test.describe('financial-health · OpenAPI contract (AC-17)', () => {
  test('/v3/api-docs documents the financial-health path', async ({ request }) => {
    const res = await request.get('/v3/api-docs');
    expect(res.status()).toBe(200);
    const doc = await res.json();
    expect(doc.paths).toHaveProperty('/api/v1/customers/{customerId}/financial-health');
    const op = doc.paths['/api/v1/customers/{customerId}/financial-health'].get;
    expect(op).toBeTruthy();
    expect(op.responses).toHaveProperty('200');
    expect(op.responses).toHaveProperty('404');
  });
});
