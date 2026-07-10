# Import Copy Rename Design

## Goal

Rename every user-visible occurrence of `辞书导入` in the frontend to `词书导入` so the navigation and import workflow use consistent terminology.

## Scope

- Update exact `辞书导入` phrases under `frontend/`, including navigation labels, page headings, action feedback, explanatory copy, and empty states.
- Keep routes, component names, API names, and import behavior unchanged.
- Do not replace other standalone `辞书` domain terms or historical documentation outside `frontend/`.

## Implementation

Add a frontend source regression test that fails while the old phrase remains, then replace each exact phrase with `词书导入`. No new abstraction is needed because this is a narrow copy-only change.

## Verification

- Run the targeted regression test and the full frontend test suite.
- Run the frontend production build.
- Confirm `辞书导入` no longer appears under `frontend/` and `词书导入` appears at every intended location.
- Rebuild and restart the unified frontend Docker container as required by the repository instructions.
- Verify the login page and frontend API proxy return HTTP 200.

## Acceptance Criteria

- The post-login left navigation displays `词书导入`.
- All other frontend text that previously displayed `辞书导入` displays `词书导入`.
- Existing import workflow behavior remains unchanged.
