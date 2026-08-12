# Releasing

## Prep (GitHub side)

1. **Ensure `main` is current:**
   ```bash
   git checkout main && git pull origin main
   ```

2. **Confirm scope** — what commits are going in?
   ```bash
   git log --oneline v$(PREVIOUS)..HEAD
   ```

3. **Agree version number** (semver: patch/minor/major).

4. **Write the changelog entry** in `CHANGELOG.md` — add a `[X.Y.Z]` section above the previous release, with a link reference at the bottom.

5. **Bump hardcoded version strings** (they live in four places):
   - `web/src/ceol/web/views.cljs` — three Replicant hiccup forms:
     - `[:div.app-version "vX.Y.Z"]`  (desktop sidebar)
     - `[:span.mobile-list-version "vX.Y.Z"]`  (mobile)
     - `[:div.settings-row-value "vX.Y.Z"]`  (settings)
   - `web/package.json` — `"version": "X.Y.Z"`

6. **Commit & tag:**
   ```bash
   git add CHANGELOG.md web/src/ceol/web/views.cljs web/package.json
   git commit -m "release: bump version to X.Y.Z + changelog"
   git tag -a vX.Y.Z -m "vX.Y.Z"
   ```

7. **Push:**
   ```bash
   git push origin main
   git push origin vX.Y.Z
   ```

## Deploy

8. **Run the release script** (build → fingerprint → zip → scp):
   ```bash
   ./release.sh
   ```

   Set `DEPLOY_SKIP=1` in `.env` to build+zip without deploying.

## Gotchas

- The version string is hardcoded in **four places** across two files — don't miss any.
- Make sure `DEPLOY_HOST` is set in `.env` before running `release.sh`.
