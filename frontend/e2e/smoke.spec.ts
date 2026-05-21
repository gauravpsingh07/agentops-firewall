import { expect, test } from '@playwright/test';

/**
 * Smoke test. Logs in as the seeded admin user, confirms the dashboard
 * shell renders, and signs back out.
 *
 * Requires a live backend on http://localhost:8080 with the local
 * profile (V2 migration seeds the demo users).
 */
test.describe('AgentOps Firewall — smoke', () => {
  test('login → dashboard → sign out', async ({ page }) => {
    await page.goto('/login');

    await expect(page.getByRole('heading', { name: 'AgentOps Firewall' })).toBeVisible();
    await expect(page.locator('input#username')).toBeVisible();

    await page.locator('input#username').fill('admin');
    await page.locator('input#password').fill('admin123');
    await page.getByRole('button', { name: /^Sign in$/i }).click();

    // The interceptor + APP_INITIALIZER hand us a populated user; the shell
    // shows the username badge.
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByText('admin', { exact: false })).toBeVisible();

    // Sign out lands us back at /login.
    await page.getByRole('button', { name: /Sign out/i }).click();
    await expect(page).toHaveURL(/\/login/);
  });
});
