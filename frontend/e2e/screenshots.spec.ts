import { APIRequestContext, Page, expect, request, test } from '@playwright/test';
import * as path from 'path';

/**
 * Screenshot-capture script. NOT a regression test — its only output is
 * eight PNGs written into `docs/screenshots/` so the README's Demo
 * section renders real images instead of filename placeholders.
 *
 * Excluded from default `npm run test:e2e` runs via testIgnore in
 * playwright.config.ts. Invoke explicitly:
 *
 *   npm run screenshots
 *
 * Prerequisites:
 *   1. docker compose up -d postgres kafka rabbitmq
 *   2. backend running on :8080 with the `local` Spring profile
 *      (Flyway seeds demo users + sample policies)
 *   3. frontend running on :4200 (`npm start`)
 *   4. chromium browser installed: `npx playwright install chromium`
 *
 * What it does:
 *   - Seeds three throwaway agents and ~7 actions in a mix of decision
 *     types (ALLOW / DENY / NEEDS_APPROVAL) so the dashboard, action
 *     feed, approval inbox, and audit log are non-empty.
 *   - Drives the UI to each protected page and takes a 1440x900 PNG.
 *   - For the simulator and audit-log pages, interacts with the form /
 *     toggle so the screenshot captures the most informative state.
 *
 * Idempotent across runs: agent names include a timestamp suffix, so a
 * second run creates fresh agents instead of colliding on duplicate
 * names. The seeded actions accumulate in the database; screenshots
 * just need *some* data to render.
 */

const SHOT_DIR = path.resolve(__dirname, '..', '..', 'docs', 'screenshots');
const BACKEND = 'http://localhost:8080';

test.describe.configure({ mode: 'serial' });

test.describe('Capture dashboard screenshots', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  let api: APIRequestContext;

  test.beforeAll(async () => {
    api = await request.newContext({ baseURL: BACKEND });

    // ---- 1. log in as admin --------------------------------------------
    const loginResp = await api.post('/api/auth/login', {
      data: { username: 'admin', password: 'admin123' }
    });
    if (!loginResp.ok()) {
      throw new Error(
        `Backend not reachable at ${BACKEND}/api/auth/login (status ${loginResp.status()}). ` +
        `Start the backend with the local profile before running screenshots.`
      );
    }
    const { token } = await loginResp.json();
    const auth = { Authorization: `Bearer ${token}` };

    // ---- 2. create three throwaway agents -------------------------------
    const stamp = Date.now();
    const agentDefs = [
      { name: `email-bot-${stamp}`,  description: 'Outbound email agent (seeded by screenshots script)' },
      { name: `deploy-bot-${stamp}`, description: 'CI deploy agent (seeded by screenshots script)' },
      { name: `api-bot-${stamp}`,    description: 'External API caller (seeded by screenshots script)' }
    ];
    const keys: Record<string, string> = {};
    for (const def of agentDefs) {
      const resp = await api.post('/api/agents', { headers: auth, data: def });
      if (!resp.ok()) {
        throw new Error(`Could not create agent ${def.name}: HTTP ${resp.status()}`);
      }
      const body = await resp.json();
      keys[def.name] = body.apiKey;
    }

    // ---- 3. submit a mix of actions to fill the dashboard --------------
    const submit = async (
      agentName: string,
      actionType: string,
      resource: string,
      riskLevel: string,
      metadata: Record<string, unknown> = {}
    ) => {
      await api.post('/api/agent-actions', {
        headers: { 'X-Agent-Key': keys[agentName] },
        data: { agentId: agentName, actionType, resource, riskLevel, metadata }
      });
    };

    const [emailBot, deployBot, apiBot] = agentDefs.map((d) => d.name);

    // ALLOWED variants
    await submit(emailBot,  'CALL_EXTERNAL_API', 'weather.api',  'LOW',    { targetDomain: 'api.weather.com' });
    await submit(apiBot,    'CALL_EXTERNAL_API', 'github.api',   'LOW',    { targetDomain: 'api.github.com' });
    await submit(apiBot,    'CALL_EXTERNAL_API', 'stripe.api',   'MEDIUM', { targetDomain: 'api.stripe.com' });

    // DENIED variants
    await submit(emailBot,  'READ_SECRET',       'vault/prod/db', 'HIGH',  {});

    // NEEDS_APPROVAL variants (these populate the approval inbox)
    await submit(deployBot, 'DELETE_FILE',       '/var/log/old.log', 'MEDIUM', { size: '2MB' });
    await submit(deployBot, 'DELETE_FILE',       '/tmp/cache.dat',   'LOW',    {});
    await submit(emailBot,  'SEND_EMAIL',        'external_email',   'MEDIUM', {
      recipientDomain: 'external.com',
      containsAttachment: true
    });
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  // -------------------------------------------------------------- shot 01

  test('01 — login page', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('input#username')).toBeVisible();
    await page.screenshot({ path: path.join(SHOT_DIR, '01-login.png') });
  });

  // -------------------------------------------------------------- shot 02

  test('02 — dashboard overview', async ({ page }) => {
    await loginAsAdmin(page);
    // Wait for the dashboard data to populate (KPI cards + bar charts).
    await page.waitForLoadState('networkidle');
    await expect(page.getByText(/Risk distribution/i)).toBeVisible();
    await page.screenshot({ path: path.join(SHOT_DIR, '02-dashboard.png'), fullPage: true });
  });

  // -------------------------------------------------------------- shot 03

  test('03 — action feed', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole('link', { name: /^Action feed$/i }).click();
    await expect(page.getByRole('heading', { name: /Action feed/i })).toBeVisible();
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(SHOT_DIR, '03-action-feed.png'), fullPage: true });
  });

  // -------------------------------------------------------------- shot 04

  test('04 — policies list', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole('link', { name: /^Policies$/i }).click();
    await expect(page.getByRole('heading', { name: /^Policies$/i })).toBeVisible();
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(SHOT_DIR, '04-policies-list.png'), fullPage: true });
  });

  // -------------------------------------------------------------- shot 05

  test('05 — policy edit', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole('link', { name: /^Policies$/i }).click();
    await page.waitForLoadState('networkidle');
    // Click the first Edit link in the policy table.
    await page.getByRole('link', { name: /^Edit$/i }).first().click();
    await expect(page.getByRole('heading', { name: /Edit policy/i })).toBeVisible();
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(SHOT_DIR, '05-policies-edit.png'), fullPage: true });
  });

  // -------------------------------------------------------------- shot 06

  test('06 — simulator', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole('link', { name: /^Simulator$/i }).click();
    await expect(page.getByRole('heading', { name: /Policy simulator/i })).toBeVisible();

    // Configure a DELETE_FILE / MEDIUM simulation so we get the yellow
    // NEEDS_APPROVAL result card — the most visually informative state.
    await page.locator('select[formControlName="actionType"]').selectOption('DELETE_FILE');
    await page.locator('select[formControlName="riskLevel"]').selectOption('MEDIUM');
    await page.locator('input[formControlName="resource"]').fill('/var/log/old.log');
    await page.getByRole('button', { name: /^Simulate$/i }).click();
    // Wait for the decision card.
    await expect(page.getByText(/Decision/i)).toBeVisible();
    await page.screenshot({ path: path.join(SHOT_DIR, '06-simulator.png'), fullPage: true });
  });

  // -------------------------------------------------------------- shot 07

  test('07 — approval inbox', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole('link', { name: /^Approvals$/i }).click();
    await expect(page.getByRole('heading', { name: /^Approvals$/i })).toBeVisible();
    await page.waitForLoadState('networkidle');
    // Confirm at least one PENDING approval is present (seeded above).
    await expect(page.getByRole('button', { name: /^Approve$/i }).first()).toBeVisible();
    await page.screenshot({ path: path.join(SHOT_DIR, '07-approvals.png'), fullPage: true });
  });

  // -------------------------------------------------------------- shot 08

  test('08 — audit log', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole('link', { name: /^Audit log$/i }).click();
    await expect(page.getByRole('heading', { name: /^Audit log$/i })).toBeVisible();
    await page.waitForLoadState('networkidle');

    // Expand the first row with a "View" button to show the pretty-
    // printed detailsJson — more informative than the default collapsed
    // table view.
    const viewBtn = page.getByRole('button', { name: /^View$/i }).first();
    if ((await viewBtn.count()) > 0) {
      await viewBtn.click();
    }
    await page.screenshot({ path: path.join(SHOT_DIR, '08-audit-log.png'), fullPage: true });
  });
});

async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('input#username').fill('admin');
  await page.locator('input#password').fill('admin123');
  await page.getByRole('button', { name: /^Sign in$/i }).click();
  await page.waitForURL(/\/dashboard/);
}
