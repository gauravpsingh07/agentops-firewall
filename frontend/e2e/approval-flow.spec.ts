import { APIRequestContext, expect, request, test } from '@playwright/test';

/**
 * Approval-flow e2e: seed a pending approval through the API, then drive
 * a reviewer through the dashboard to approve it.
 *
 * Seed path:
 *  1. Log in as admin via /api/auth/login and grab the JWT.
 *  2. Create a one-off agent via POST /api/agents — the raw API key is
 *     returned exactly once in the response.
 *  3. Submit a DELETE_FILE action via POST /api/agent-actions with that
 *     agent key. The sample policies seeded under the local profile
 *     require approval for DELETE_FILE, so the firewall returns
 *     NEEDS_APPROVAL and creates a PENDING ApprovalRequest.
 *
 * Drive path:
 *  4. Open the dashboard as reviewer, navigate to /dashboard/approvals.
 *  5. Click Approve on the seeded approval (which is the first PENDING
 *     card since approvals are sorted newest-first).
 *  6. Expect the success toast wired in the previous commit.
 *
 * Requires a live local stack with seed data: backend on :8080 with the
 * `local` profile (Flyway V2 + V3 migrations seed demo users and the
 * sample policy set), frontend on :4200. Not run by default CI.
 */
test.describe('AgentOps Firewall — approval flow', () => {
  let api: APIRequestContext;

  test.beforeAll(async ({ playwright }) => {
    api = await request.newContext({ baseURL: 'http://localhost:8080' });
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('reviewer approves a seeded DELETE_FILE action', async ({ page }) => {
    // ---- 1. admin token ---------------------------------------------------
    const loginResp = await api.post('/api/auth/login', {
      data: { username: 'admin', password: 'admin123' }
    });
    expect(loginResp.status(), 'admin login').toBe(200);
    const { token } = await loginResp.json();
    expect(token).toBeTruthy();

    // ---- 2. create a throwaway agent --------------------------------------
    const agentName = `e2e-agent-${Date.now()}`;
    const agentResp = await api.post('/api/agents', {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: agentName, description: 'created by Playwright approval-flow spec' }
    });
    expect(agentResp.status(), 'create agent').toBe(201);
    const agentBody = await agentResp.json();
    const agentKey = agentBody.apiKey as string;
    expect(agentKey).toMatch(/^agk_/);

    // ---- 3. submit a DELETE_FILE action -> NEEDS_APPROVAL ------------------
    const submitResp = await api.post('/api/agent-actions', {
      headers: { 'X-Agent-Key': agentKey },
      data: {
        agentId: agentName,
        actionType: 'DELETE_FILE',
        resource: 'e2e-fixture.txt',
        riskLevel: 'MEDIUM',
        metadata: { source: 'playwright' }
      }
    });
    expect(submitResp.status(), 'submit action').toBe(201);
    const submitBody = await submitResp.json();
    expect(submitBody.decision).toBe('NEEDS_APPROVAL');
    expect(submitBody.approvalId).toBeTruthy();

    // ---- 4. log in as reviewer in the browser ------------------------------
    await page.goto('/login');
    await page.locator('input#username').fill('reviewer');
    await page.locator('input#password').fill('reviewer123');
    await page.getByRole('button', { name: /^Sign in$/i }).click();
    await expect(page).toHaveURL(/\/dashboard/);

    await page.getByRole('link', { name: /Approvals/i }).click();
    await expect(page).toHaveURL(/\/dashboard\/approvals/);

    // ---- 5. approve the seeded pending row ---------------------------------
    const approveBtn = page.getByRole('button', { name: /^Approve$/i }).first();
    await expect(approveBtn, 'at least one PENDING approval visible').toBeVisible();
    await approveBtn.click();

    // ---- 6. confirm the success toast appears ------------------------------
    await expect(
      page.getByRole('region', { name: /Notifications/i }).getByText(/Approval granted/i)
    ).toBeVisible({ timeout: 5_000 });
  });
});
