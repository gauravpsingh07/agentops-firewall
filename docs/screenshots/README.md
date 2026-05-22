# Screenshots

Visual record of each Angular page in the dashboard. Drop a PNG with the
filename listed below into this folder and it'll be picked up
automatically by the Demo section of the main README.

Capture guidance:
- Sign in as **admin** so role-locked sections (Approvals, policy
  controls) are visible.
- Use the default Tailwind dark theme; the screenshots in this folder
  should match what a first-time visitor sees.
- 1440×900 viewport works well; PNG, lossless, 72–96 DPI.
- Crop the browser chrome out (don't include the URL bar, tabs, OS
  window decoration).

## Expected files

| Filename | What it shows |
|---|---|
| `01-login.png` | Login page with the demo-credentials hint. |
| `02-dashboard.png` | Dashboard landing — KPI cards, risk + decision charts, recent activity. |
| `03-action-feed.png` | Action feed with filters applied and a mix of ALLOWED/DENIED/PENDING_APPROVAL rows. |
| `04-policies-list.png` | Policy management — list view sorted by priority, with enable/disable toggles. |
| `05-policies-edit.png` | Policy editor — conditions FormArray with a few rows. |
| `06-simulator.png` | Policy simulator with a result card (any decision colour). |
| `07-approvals.png` | Approval inbox — at least one PENDING card with the note input + approve/reject buttons. |
| `08-audit-log.png` | Audit log — expanded row showing pretty-printed `detailsJson`. |

## Capture script

The capture is automated by `frontend/e2e/screenshots.spec.ts`, run via:

```bash
cd frontend
npm run screenshots
```

The script signs in as **admin**, drives each route, and writes the
eight PNGs above directly into this folder. Prerequisites: the full
local stack must be running (`docker compose up -d postgres kafka
rabbitmq`, then the backend on 8080 and the Angular dev server on
4200) — see the root README's "Local development" section for the
end-to-end procedure.

Re-run the script after any UI change so the screenshots committed
to this folder stay in sync with the current dashboard.
