import { test, expect } from '@playwright/test';

/**
 * Black-box HTTP API acceptance tests for
 *   GET /api/v1/customers/{customerId}/spending-summary
 *
 * Deterministic Playwright Test (`request` / APIRequestContext) tests, run against a LOCALLY
 * started app only — never a real/production environment (testing-standards). All GET, read-only.
 *
 * Derived from the approved spec v1 acceptance criteria:
 *   AC-1  known customer → 200 application/json
 *   AC-5  deterministic ordering (desc total, name asc on tie), grandTotal, topCategory, count
 *   AC-7  empty-result customer → 200 empty shape, never 404
 *   AC-8  error paths → RFC 7807 application/problem+json, no stack-trace leak
 *   AC-9  /v3/api-docs documents the spending-summary path
 *   AC-10 CUST-000002 multi-category tie against additive fixtures
 *   AC-11 Playwright acceptance coverage of the above
 *
 * Fixtures (deterministic synthetic seed — fintech-customer-insights/.../FixtureData.java):
 *   CUST-000002  Alan / USD  ELECTRONICS 850.00, GROCERIES 200.00, TRANSPORT 200.00 (tie)
 *   CUST-000003  Grace / EUR empty transactions
 *   CUST-000004  reserved id → deterministic dependency-failure seam (503)
 *   CUST-999999  reserved well-formed id, never present (404)
 *
 * Monetary scale note: the API serializes BigDecimal with an explicit scale on the wire
 * (e.g. `"totalSpent":850.00`). JSON.parse normalizes numbers to IEEE doubles and drops trailing
 * zeros, so scale is asserted against the raw response text; numeric/order values after parsing.
 */

const MULTI = 'CUST-000002';
const EMPTY = 'CUST-000003';
const DEP_FAIL = 'CUST-000004';
const UNKNOWN = 'CUST-999999';
const MALFORMED = 'not-a-valid-id';

function path(id: string): string {
  return `/api/v1/customers/${id}/spending-summary`;
}

test.describe('spending-summary · success (AC-1, AC-5, AC-10, AC-11)', () => {
  test('multi-category customer returns 200 application/json', async ({ request }) => {
    const res = await request.get(path(MULTI));
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('application/json');
  });

  test('multi-category ordering, totals, top category, count', async ({ request }) => {
    const res = await request.get(path(MULTI));
    expect(res.status()).toBe(200);
    const raw = await res.text();
    const body = JSON.parse(raw);

    // Deterministic ordering: desc total, then category name asc on the 200.00 tie.
    expect(body.categories.map((c: { category: string }) => c.category))
      .toEqual(['ELECTRONICS', 'GROCERIES', 'TRANSPORT']);

    // On-the-wire scale (money scale 2).
    expect(raw).toContain('"totalSpent":850.00');
    expect(raw).toContain('"totalSpent":200.00');
    expect(raw).toContain('"grandTotal":1250.00');
    expect(raw).toContain('"topCategory":"ELECTRONICS"');
    expect(raw).toContain('"debitTransactionCount":4');
    expect(raw).toContain('"currency":"USD"');

    expect(String(body.disclaimer).toLowerCase()).toContain('synthetic');
  });
});

test.describe('spending-summary · empty data (AC-7, AC-11)', () => {
  test('empty-result customer returns 200 empty shape', async ({ request }) => {
    const res = await request.get(path(EMPTY));
    expect(res.status()).toBe(200);
    const raw = await res.text();
    const body = JSON.parse(raw);

    expect(Array.isArray(body.categories)).toBeTruthy();
    expect(body.categories.length).toBe(0);
    expect(raw).toContain('"grandTotal":0.00');
    expect(body.topCategory).toBeNull();
    expect(raw).toContain('"debitTransactionCount":0');
  });
});

test.describe('spending-summary · errors (AC-8, AC-11)', () => {
  test('error paths return RFC 7807 problem+json', async ({ request }) => {
    // Malformed id → 400
    const malformed = await request.get(path(MALFORMED));
    expect(malformed.status()).toBe(400);
    expect(malformed.headers()['content-type']).toContain('application/problem+json');

    // from > to → 400 with field=from
    const inverted = await request.get(
      `${path(MULTI)}?from=2025-03-01T00:00:00Z&to=2025-01-01T00:00:00Z`,
    );
    expect(inverted.status()).toBe(400);
    expect(inverted.headers()['content-type']).toContain('application/problem+json');
    const invertedBody = await inverted.json();
    expect(invertedBody).toHaveProperty('field', 'from');

    // Unknown customer → 404
    const unknown = await request.get(path(UNKNOWN));
    expect(unknown.status()).toBe(404);
    expect(unknown.headers()['content-type']).toContain('application/problem+json');

    // Dependency failure → 503
    const depFail = await request.get(path(DEP_FAIL));
    expect(depFail.status()).toBe(503);
    expect(depFail.headers()['content-type']).toContain('application/problem+json');

    for (const res of [malformed, inverted, unknown, depFail]) {
      const body = await res.json();
      expect(body).toHaveProperty('type');
      expect(body).toHaveProperty('title');
      expect(body).toHaveProperty('status');
      expect(body).toHaveProperty('detail');
      expect(JSON.stringify(body)).not.toMatch(/at com\.example\.fintech/);
    }
  });
});

test.describe('spending-summary · OpenAPI contract (AC-9, AC-11)', () => {
  test('OpenAPI documents the spending-summary path', async ({ request }) => {
    const res = await request.get('/v3/api-docs');
    expect(res.status()).toBe(200);
    const doc = await res.json();
    expect(doc.paths).toHaveProperty('/api/v1/customers/{customerId}/spending-summary');
    const op = doc.paths['/api/v1/customers/{customerId}/spending-summary'].get;
    expect(op).toBeTruthy();
    expect(op.responses).toHaveProperty('200');
    expect(op.responses).toHaveProperty('404');
  });
});
