import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for AgentOps Firewall.
 *
 * The e2e suite runs against a live local stack (backend on :8080 and
 * frontend on :4200). It is intentionally NOT executed by the default
 * GitHub Actions workflow — a separate `e2e.yml` workflow brings up
 * docker-compose and invokes Playwright on demand.
 *
 * The screenshot-capture script (`e2e/screenshots.spec.ts`) is excluded
 * from default runs via `testIgnore` because it writes artefacts into
 * `docs/screenshots/`, which is not test behaviour. Run it explicitly
 * with `npm run screenshots`.
 *
 * Local usage:
 *   # one-time browser install:
 *   npx playwright install chromium
 *
 *   # in one terminal:
 *   cd backend && ./mvnw spring-boot:run
 *   cd frontend && npm start
 *
 *   # in another terminal:
 *   cd frontend && npm run test:e2e        # smoke + approval-flow
 *   cd frontend && npm run screenshots     # capture docs/screenshots/*.png
 */
const BASE_URL = process.env['E2E_BASE_URL'] ?? 'http://localhost:4200';

export default defineConfig({
  testDir: './e2e',
  testIgnore: '**/screenshots.spec.ts',
  fullyParallel: false,
  retries: process.env['CI'] ? 2 : 0,
  workers: 1,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
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
