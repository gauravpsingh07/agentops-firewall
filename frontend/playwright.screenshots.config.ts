import { defineConfig, devices } from '@playwright/test';

/**
 * Separate config for the screenshot-capture script.
 *
 * The main playwright.config.ts uses `testIgnore` to exclude
 * `screenshots.spec.ts` from regular e2e runs (the script writes
 * artefacts, not test results, and shouldn't pollute test outputs).
 * Playwright applies `testIgnore` even when a file is named explicitly
 * on the CLI, so a separate config is the cleanest way to invoke the
 * screenshot script while keeping the default run focused.
 *
 * Usage:
 *   cd frontend
 *   npm run screenshots
 *   # equivalent to:
 *   #   npx playwright test --config playwright.screenshots.config.ts
 */
const BASE_URL = process.env['E2E_BASE_URL'] ?? 'http://localhost:4200';

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/screenshots.spec.ts',
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: BASE_URL,
    actionTimeout: 10_000,
    navigationTimeout: 30_000
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
