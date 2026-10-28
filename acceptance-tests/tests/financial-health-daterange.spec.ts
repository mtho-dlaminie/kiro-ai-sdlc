import { test, expect } from '@playwright/test';

/**
 * Black-box HTTP API acceptance tests for the DATE-RANGE SCOPING feature of
 *   GET /api/v1/customers/{customerId}/financial-health?from=&to=
 *
 * Run id: run-gh-2-financial-health-daterange  (GitHub issue #2).
 *
 * Deterministic Playwright Test (`request` / APIRequestContext) tests, run against a LOCALLY
 * started app only — never a real/production environment (testing-standards, api-verifier rules).
 *
 * Derived from the approved spec v1 (.agents/runs/run-gh-2-financial-health-daterange/spec.md)
 * acceptance criteria AC-1..AC-13. Every expected number below is PINNED to the spec's §4 /
 * §AC tables, which were computed from the merged formulas and the deterministic synthetic
 * fixtures (FixtureData.java). No clock/randomness is involved, so these are reproducible.
 *
 * Deterministic fixture for CUST-000001 (Ada / GBP), credit: debt 1200.00, utilization 18.50%:
 *   TXN-5002  +2500.00 CREDIT SALARY    2025-01-01T00:00:00Z
 *   TXN-5001    -45.20 DEBIT  GROCERIES 2025-01-05T00:00:00Z
 *   TXN-5003   +250.75 CREDIT INTEREST  2025-01-31T00:00:00Z
 *
 * Range semantics (BR-SCOPE): both bounds INCLUSIVE; each null bound is open-ended.
 *
 * Monetary scale note: the API serializes BigDecimal with an explicit scale on the wire
 * (e.g. `"monthlyExpenses":45.20`). JavaScript's JSON.parse normalizes numbers to IEEE doubles
 * and drops trailing zeros, so scale is asserted against the raw response text, while numeric
 * business values are asserted after parsing. This checks both the contract and the arithmetic.
 */

const FULL = 'CUST-000001'; // Ada / GBP, full data
const UNKNOWN = 'CUST-999999'; // well-formed, absent
const DEP_FAIL = 'CUST-000004'; // deterministic dependency-failure seam
const BAD_ID = 'BADID'; // violates ^CUST-\d{6}$

function path(id: string): string {
  return `/api/v1/customers/${id}/financial-health`;
}

/** Assert a numeric field appears in the raw JSON with the exact literal, preserving scale. */
function expectRawNumberLiteral(rawBody: string, field: string, literal: string): void {
  expect(rawBody).toContain(`"${field}":${literal}`);
}

test.describe('financial-health date-range · scoped derivation (AC-1)', () => {
  test('explicit [from,to] window scopes the derivation to in-range transactions', async ({ request }) => {
    // 2025-01-01..2025-01-10 selects salary (01-01) + groceries (01-05); interest (01-31) excluded.
    const res = await request.get(path(FULL), {
      params: { from: '2025-01-01T00:00:00Z', to: '2025-01-10T00:00:00Z' },
    });
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('application/json');

    const raw = await res.text();
    const body = JSON.parse(raw);

    expect(body).toMatchObject({ customerId: FULL, currency: 'GBP' });
    // Pinned spec §4 / AC-1 values.
    expectRawNumberLiteral(raw, 'monthlyIncome', '2500.00');
    expectRawNumberLiteral(raw, 'monthlyExpenses', '45.20');
    expectRawNumberLiteral(raw, 'savingsRate', '0.9819');
    expectRawNumberLiteral(raw, 'debtToIncomeRatio', '0.4800');
    expect(body.monthlyIncome).toBeCloseTo(2500.0, 2);
    expect(body.monthlyExpenses).toBeCloseTo(45.2, 2);
    expect(body.savingsRate).toBeCloseTo(0.9819, 4);
    expect(body.debtToIncomeRatio).toBeCloseTo(0.48, 4);
    expect(body.financialHealthScore).toBe(81);
    expect(body.rating).toBe('EXCELLENT');
    expect(String(body.disclaimer).toLowerCase()).toContain('synthetic');
  });
});

test.describe('financial-health date-range · default unscoped regression (AC-2)', () => {
  test('no from/to returns the full-history body unchanged', async ({ request }) => {
    const res = await request.get(path(FULL));
    expect(res.status()).toBe(200);
    const raw = await res.text();
    const body = JSON.parse(raw);

    // Equals the merged all-transactions response (regression safety, REQ-3).
    expectRawNumberLiteral(raw, 'monthlyIncome', '2750.75');
    expectRawNumberLiteral(raw, 'monthlyExpenses', '45.20');
    expectRawNumberLiteral(raw, 'savingsRate', '0.9836');
    expectRawNumberLiteral(raw, 'debtToIncomeRatio', '0.4362');
    expect(body.financialHealthScore).toBe(82);
    expect(body.rating).toBe('EXCELLENT');
  });

  test('empty from/to query strings behave as unscoped default', async ({ request }) => {
    // Absent-vs-empty: blank params must not break binding; treated as open-ended.
    const res = await request.get(path(FULL), { params: { from: '', to: '' } });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.financialHealthScore).toBe(82);
    expect(body.rating).toBe('EXCELLENT');
  });
});

test.describe('financial-health date-range · single open-ended bound (AC-3)', () => {
  test('from only scopes to [from, +inf) — inclusive lower bound', async ({ request }) => {
    // from=2025-01-10 -> only interest (01-31); salary (01-01) and groceries (01-05) excluded.
    const res = await request.get(path(FULL), { params: { from: '2025-01-10T00:00:00Z' } });
    expect(res.status()).toBe(200);
    const raw = await res.text();
    const body = JSON.parse(raw);
    expectRawNumberLiteral(raw, 'monthlyIncome', '250.75');
    expectRawNumberLiteral(raw, 'monthlyExpenses', '0.00');
    expectRawNumberLiteral(raw, 'savingsRate', '1.0000');
    expectRawNumberLiteral(raw, 'debtToIncomeRatio', '4.7856');
    expect(body.monthlyIncome).toBeCloseTo(250.75, 2);
    expect(body.monthlyExpenses).toBe(0);
    expect(body.financialHealthScore).toBe(66);
    expect(body.rating).toBe('GOOD');
  });

  test('to only scopes to (-inf, to] — inclusive upper bound', async ({ request }) => {
    // to=2025-01-10 -> salary (01-01) + groceries (01-05); interest (01-31) excluded.
    const res = await request.get(path(FULL), { params: { to: '2025-01-10T00:00:00Z' } });
    expect(res.status()).toBe(200);
    const raw = await res.text();
    const body = JSON.parse(raw);
    expectRawNumberLiteral(raw, 'monthlyIncome', '2500.00');
    expectRawNumberLiteral(raw, 'savingsRate', '0.9819');
    expect(body.monthlyIncome).toBeCloseTo(2500.0, 2);
  });
});

test.describe('financial-health date-range · boundary inclusivity (AC-11 / BR-SCOPE)', () => {
  test('a transaction exactly on the from bound is included', async ({ request }) => {
    // from == to == salary timestamp (2025-01-01): the single boundary txn is included.
    const res = await request.get(path(FULL), {
      params: { from: '2025-01-01T00:00:00Z', to: '2025-01-01T00:00:00Z' },
    });
    expect(res.status()).toBe(200);
    const raw = await res.text();
    const body = JSON.parse(raw);
    // Only the 2500.00 salary credit falls on exactly 2025-01-01.
    expectRawNumberLiteral(raw, 'monthlyIncome', '2500.00');
    expectRawNumberLiteral(raw, 'monthlyExpenses', '0.00');
    expect(body.monthlyIncome).toBeCloseTo(2500.0, 2);
    expect(body.monthlyExpenses).toBe(0);
  });

  test('identical requests return identical bodies (deterministic, AC-11)', async ({ request }) => {
    const params = { from: '2025-01-01T00:00:00Z', to: '2025-01-10T00:00:00Z' };
    const a = await (await request.get(path(FULL), { params })).text();
    const b = await (await request.get(path(FULL), { params })).text();
    expect(a).toBe(b);
  });
});

test.describe('financial-health date-range · empty period (AC-7)', () => {
  test('valid range selecting zero transactions returns 200 zeros, never 404', async ({ request }) => {
    // All of 2024 — no fixture transactions fall in this window.
    const res = await request.get(path(FULL), {
      params: { from: '2024-01-01T00:00:00Z', to: '2024-12-31T00:00:00Z' },
    });
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('application/json');
    const raw = await res.text();
    const body = JSON.parse(raw);
    expectRawNumberLiteral(raw, 'monthlyIncome', '0.00');
    expectRawNumberLiteral(raw, 'monthlyExpenses', '0.00');
    expectRawNumberLiteral(raw, 'savingsRate', '0.0000');
    // income 0 with debt 1200.00 > 0 -> DTI capped high per BR-4.
    expectRawNumberLiteral(raw, 'debtToIncomeRatio', '9.9999');
    expect(body.financialHealthScore).toBe(16);
    expect(body.rating).toBe('POOR');
  });
});

test.describe('financial-health date-range · invalid range (AC-4)', () => {
  test('from strictly after to returns 400 problem+json, field=from', async ({ request }) => {
    const res = await request.get(path(FULL), {
      params: { from: '2025-02-01T00:00:00Z', to: '2025-01-01T00:00:00Z' },
    });
    expect(res.status()).toBe(400);
    expect(res.headers()['content-type']).toContain('application/problem+json');
    const body = await res.json();
    expect(body).toHaveProperty('status', 400);
    expect(body.type).toBe('urn:fintech:error:invalid-request');
    expect(body.field).toBe('from');
    expect(body).toHaveProperty('correlationId');
    // No summary body fields leak through on an error.
    expect(body).not.toHaveProperty('monthlyIncome');
    expect(JSON.stringify(body)).not.toMatch(/at com\.example\.fintech|Exception:/);
  });
});

test.describe('financial-health date-range · malformed date (AC-5)', () => {
  test('non-ISO from returns 400 problem+json, field=from', async ({ request }) => {
    const res = await request.get(path(FULL), { params: { from: 'not-a-date' } });
    expect(res.status()).toBe(400);
    expect(res.headers()['content-type']).toContain('application/problem+json');
    const body = await res.json();
    expect(body).toHaveProperty('status', 400);
    expect(body.type).toBe('urn:fintech:error:invalid-request');
    expect(body.field).toBe('from');
    expect(body).toHaveProperty('correlationId');
    expect(JSON.stringify(body)).not.toMatch(/at com\.example\.fintech|Exception:/);
  });

  test('non-ISO to returns 400 problem+json, field=to', async ({ request }) => {
    const res = await request.get(path(FULL), { params: { to: '31/01/2025' } });
    expect(res.status()).toBe(400);
    expect(res.headers()['content-type']).toContain('application/problem+json');
    const body = await res.json();
    expect(body).toHaveProperty('status', 400);
    expect(body.type).toBe('urn:fintech:error:invalid-request');
    expect(body.field).toBe('to');
  });
});

test.describe('financial-health date-range · malformed id (AC-6)', () => {
  test('bad customerId with a range returns 400 validation problem, errors[] field=customerId', async ({ request }) => {
    const res = await request.get(path(BAD_ID), {
      params: { from: '2025-01-01T00:00:00Z', to: '2025-01-31T00:00:00Z' },
    });
    expect(res.status()).toBe(400);
    expect(res.headers()['content-type']).toContain('application/problem+json');
    const body = await res.json();
    expect(body).toHaveProperty('status', 400);
    expect(body.type).toBe('urn:fintech:error:validation');
    expect(Array.isArray(body.errors)).toBeTruthy();
    expect(body.errors.some((e: { field: string }) => e.field === 'customerId')).toBeTruthy();
    expect(JSON.stringify(body)).not.toMatch(/at com\.example\.fintech|Exception:/);
  });
});

test.describe('financial-health date-range · unknown customer (AC-8)', () => {
  test('absent customer with a valid range returns 404 problem+json', async ({ request }) => {
    const res = await request.get(path(UNKNOWN), {
      params: { from: '2025-01-01T00:00:00Z', to: '2025-01-31T00:00:00Z' },
    });
    expect(res.status()).toBe(404);
    expect(res.headers()['content-type']).toContain('application/problem+json');
    const body = await res.json();
    expect(body).toHaveProperty('status', 404);
    expect(body.type).toBe('urn:fintech:error:customer-not-found');
    expect(body).toHaveProperty('correlationId');
    expect(JSON.stringify(body)).not.toMatch(/at com\.example\.fintech|Exception:/);
  });
});

test.describe('financial-health date-range · dependency failure (AC-9)', () => {
  test('dependency-failure customer with a range returns 503 problem+json', async ({ request }) => {
    const res = await request.get(path(DEP_FAIL), {
      params: { from: '2025-01-01T00:00:00Z', to: '2025-01-31T00:00:00Z' },
    });
    expect(res.status()).toBe(503);
    expect(res.headers()['content-type']).toContain('application/problem+json');
    const body = await res.json();
    expect(body).toHaveProperty('status', 503);
    expect(body.type).toBe('urn:fintech:error:dependency-unavailable');
    expect(JSON.stringify(body)).not.toMatch(/at com\.example\.fintech/);
  });
});

test.describe('financial-health date-range · OpenAPI contract (AC-10)', () => {
  test('/v3/api-docs documents from and to query params with 200/400/404/503', async ({ request }) => {
    const res = await request.get('/v3/api-docs');
    expect(res.status()).toBe(200);
    const doc = await res.json();
    const op = doc.paths?.['/api/v1/customers/{customerId}/financial-health']?.get;
    expect(op).toBeTruthy();
    const paramNames: string[] = (op.parameters ?? []).map((p: { name: string }) => p.name);
    expect(paramNames).toContain('from');
    expect(paramNames).toContain('to');
    for (const code of ['200', '400', '404', '503']) {
      expect(op.responses).toHaveProperty(code);
    }
  });
});
