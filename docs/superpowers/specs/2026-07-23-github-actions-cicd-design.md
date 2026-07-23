# GitHub Actions CI/CD for music-cat — Design

**Date:** 2026-07-23
**Status:** Approved design → implementation plan next

## Goal

Replace the manual, local `./deploy.sh` flow with automated GitHub Actions:

- **On every PR to `master`:** run backend tests + build the frontend (TS typecheck). A red build cannot merge.
- **On merge/push to `master`:** re-run tests, then build the container and deploy a new Cloud Run revision — with **no local machine involved**.

Authentication to GCP is **keyless** (Workload Identity Federation). Runtime config is supplied from **GitHub Actions secrets**.

## Hard constraints (carried from existing project memory)

- **Personal Google account only** (`gibsonshamray@gmail.com`) for all GCP/GitHub/Google operations — never the LoopMe work account. The developer's local `gcloud` is currently on the work account, so all one-time GCP setup runs under a dedicated personal named config.
- **$0 guardrails preserved:** Artifact Registry keep-2-images cleanup policy, no Container Scanning, min-instances=0/max-instances=1, `*.run.app` URL. CI/CD adds no always-on cost (GitHub-hosted runners; Cloud Run still scales to zero).
- The deployed service config must stay identical to `deploy.sh` (same flags, env vars, secret mount).

## Fixed values

| Thing | Value |
|---|---|
| GCP project | `music-cat-hosting` |
| GCP project number | `1054388126708` *(verify in setup — see below)* |
| Region | `europe-west1` |
| Cloud Run service | `music-cat` |
| Artifact Registry repo | `music-cat` |
| Image | `europe-west1-docker.pkg.dev/music-cat-hosting/music-cat/app:<short-sha>` |
| Runtime SA (service runs as) | `1054388126708-compute@developer.gserviceaccount.com` |
| Deployer SA (new) | `gh-deployer@music-cat-hosting.iam.gserviceaccount.com` |
| WIF pool / provider (new) | `github-pool` / `github-provider` |
| GitHub repo | `alexshamrai/music-cat` |
| Java (CI) | Temurin 25 |
| Node (CI) | 22.12.0 (matches `frontend/build.gradle.kts`) |
| Personal account | `gibsonshamray@gmail.com` |

## Decisions (with rationale)

1. **Auth: Workload Identity Federation (keyless).** GitHub's OIDC token is trusted by a GCP workload identity pool, which lets the workflow impersonate a deployer service account. No long-lived SA JSON key is ever stored in GitHub. Matches the project's existing security posture (fail-fast on default creds, XHR-header CSRF guard, etc.).
2. **Runtime config via GitHub secrets.** `MUSIC_CAT_USER`, `MUSIC_CAT_PASSWORD`, `SHEETS_SPREADSHEET_ID` are stored as GitHub Actions secrets and passed on every deploy with `--set-env-vars`, mirroring `deploy.sh` exactly. GitHub becomes the source of truth for these three values.
3. **Test gate + deploy split.** A `ci.yml` gates PRs; a `deploy.yml` self-gates (its own `test` job runs before the `deploy` job) so even a *direct push* to `master` is tested before it deploys. "Never auto-deploy a red build."
4. **CI includes the frontend build.** `ci.yml` runs `npm ci && npm run build` so TypeScript/Vite errors surface on the PR, not at deploy time.
5. **`deploy.sh` is kept** as a manual fallback (emergency local deploy).

## Components

### 1. `.github/workflows/ci.yml` — PR gate

- Trigger: `pull_request` targeting `master`.
- Two parallel jobs on `ubuntu-latest`:
  - **backend:** `actions/checkout` → `actions/setup-java` (temurin 25) → `gradle/actions/setup-gradle` (Gradle caching) → `./gradlew :backend:test --no-daemon`.
  - **frontend:** `actions/checkout` → `actions/setup-node` (node 22, cache npm) → `npm ci` (in `frontend/`) → `npm run build`.
- No secrets, no GCP, no `id-token` permission needed.

### 2. `.github/workflows/deploy.yml` — build + deploy on merge

- Trigger: `push` to `master` **and** `workflow_dispatch` (manual button).
- `concurrency: { group: deploy-master, cancel-in-progress: false }` — queue overlapping deploys instead of racing/cancelling mid-deploy.
- Non-secret identifiers live in `env:` at the workflow level (project, region, service, AR repo, WIF provider resource name, deployer SA email). These are not secrets.
- **Job `test`** (`ubuntu-latest`): same backend test run as CI, on the merge commit.
- **Job `deploy`** (`needs: test`, `permissions: { contents: read, id-token: write }`):
  1. `actions/checkout`
  2. `google-github-actions/auth@v2` with `workload_identity_provider` + `service_account` (keyless).
  3. `google-github-actions/setup-gcloud@v2`.
  4. `gcloud auth configure-docker europe-west1-docker.pkg.dev --quiet`.
  5. Build & push: `docker build -t $IMAGE .` then `docker push $IMAGE`, where `IMAGE=…/app:${GITHUB_SHA::7}`. Runners are amd64 → **no buildx cross-compile** (unlike `deploy.sh` on the arm64 Mac).
  6. Deploy — **mirrors `deploy.sh` exactly**:
     ```
     gcloud run deploy music-cat \
       --project=music-cat-hosting --image=$IMAGE --region=europe-west1 \
       --allow-unauthenticated \
       --min-instances=0 --max-instances=1 --cpu=1 --memory=1Gi --cpu-boost \
       --set-env-vars=SPRING_PROFILES_ACTIVE=cloud,SHEETS_SPREADSHEET_ID=***,MUSIC_CAT_USER=***,MUSIC_CAT_PASSWORD=*** \
       --set-secrets=/secrets/google/credentials.json=sheets-sa-key:latest \
       --quiet
     ```
  7. Print the resulting service URL.

### 3. One-time GCP setup script (developer runs, personal account)

Runs under a dedicated personal named config so it never clobbers the work `gcloud`:

```bash
# --- Switch gcloud to the PERSONAL account (interactive browser login) ---
gcloud config configurations create music-cat 2>/dev/null || gcloud config configurations activate music-cat
gcloud auth login                      # ← choose gibsonshamray@gmail.com in the browser
gcloud config set account gibsonshamray@gmail.com
gcloud config set project music-cat-hosting

PROJECT_ID=music-cat-hosting
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')   # verify == 1054388126708
REPO=alexshamrai/music-cat
RUNTIME_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
DEPLOYER_SA="gh-deployer@${PROJECT_ID}.iam.gserviceaccount.com"

# 1. Enable WIF-related APIs
gcloud services enable iamcredentials.googleapis.com sts.googleapis.com --project="$PROJECT_ID"

# 2. Deployer service account
gcloud iam service-accounts create gh-deployer \
  --project="$PROJECT_ID" --display-name="GitHub Actions deployer"

# 3. Grant the deployer only what it needs
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$DEPLOYER_SA" --role="roles/run.admin"
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$DEPLOYER_SA" --role="roles/artifactregistry.writer"
# Needed so the deployer can deploy a revision that RUNS AS the runtime SA:
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --project="$PROJECT_ID" \
  --member="serviceAccount:$DEPLOYER_SA" --role="roles/iam.serviceAccountUser"

# 4. Workload identity pool + GitHub OIDC provider (locked to the one repo)
gcloud iam workload-identity-pools create github-pool \
  --project="$PROJECT_ID" --location=global --display-name="GitHub Actions pool"

gcloud iam workload-identity-pools providers create-oidc github-provider \
  --project="$PROJECT_ID" --location=global --workload-identity-pool=github-pool \
  --display-name="GitHub OIDC" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
  --attribute-condition="assertion.repository=='${REPO}'"

# 5. Let ONLY this repo's WIF principal impersonate the deployer SA
gcloud iam service-accounts add-iam-policy-binding "$DEPLOYER_SA" \
  --project="$PROJECT_ID" --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/attribute.repository/${REPO}"

# Value for the workflow env WIF_PROVIDER:
echo "projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/providers/github-provider"
```

**Security control:** the provider's `attribute-condition` restricts token exchange to `alexshamrai/music-cat`. Without it, any GitHub repo on the internet could impersonate the deployer SA. This is mandatory, not optional.

### 4. GitHub repo configuration (developer, personal account, web UI or `gh`)

Add three **Actions secrets** (Settings → Secrets and variables → Actions):

- `MUSIC_CAT_USER`
- `MUSIC_CAT_PASSWORD`
- `SHEETS_SPREADSHEET_ID` (`120QOqIDJG0iM0yG7C2Kus--gptc4EGKIsp7I8pTGoN4`)

The WIF provider resource name and deployer SA email are **not** secrets and are hardcoded in `deploy.yml`'s `env:` block.

## Data / trust flow

```
PR  ──▶ ci.yml (backend tests + frontend build) ──▶ status check
merge to master
   └─▶ deploy.yml
        test job (backend tests)
          └─(needs)─▶ deploy job
              GitHub OIDC token
                └─▶ GCP WIF provider (repo == alexshamrai/music-cat?)
                     └─▶ impersonate gh-deployer SA
                          ├─▶ docker push → Artifact Registry (music-cat repo)
                          └─▶ gcloud run deploy → new Cloud Run revision
                                (runs as compute SA; reads sheets-sa-key at runtime)
```

## Failure handling

- **Tests fail** → `deploy` job never runs (`needs: test`). No revision created.
- **Docker build/push fails** → deploy step fails; last good revision keeps serving.
- **`gcloud run deploy` fails** → Cloud Run keeps the previous healthy revision serving (revisions are immutable; traffic only shifts on success).
- **Wrong repo tries to use the provider** → token exchange denied by the attribute condition.
- **Concurrency** → a second merge queues behind an in-flight deploy rather than racing it.

## Sequencing (must be in this order)

1. Developer runs the GCP setup script (personal account) → captures the `WIF_PROVIDER` value.
2. Developer adds the three GitHub secrets.
3. `ci.yml` + `deploy.yml` are committed and land on `master`.
4. First auto-deploy runs on that push (or via `workflow_dispatch`) and is verified.

Reason: if the workflows land before the trust path + secrets exist, the first auto-deploy fails.

## Verification plan

- **CI:** open a throwaway PR with a trivial change → confirm `ci.yml` runs both jobs green; introduce a deliberate TS error → confirm the frontend job fails.
- **Deploy:** trigger `deploy.yml` via `workflow_dispatch` (or the landing push) → confirm auth succeeds, image pushes, a new revision (`music-cat-000NN`) is created, and the printed URL loads and requires Basic auth.
- **Config parity:** confirm the new revision has `SPRING_PROFILES_ACTIVE=cloud`, the three env vars, and the `sheets-sa-key` mount (compare against a `deploy.sh`-produced revision).
- **Guardrails:** confirm Artifact Registry still holds ≤2 images after a couple of deploys (cleanup policy intact).

## Out of scope / non-goals

- No change to app code, Dockerfile, or Cloud Run resource sizing.
- No migration of the three env-var values into Secret Manager (they stay as env vars, per decision 2).
- No staging environment / traffic-splitting / blue-green (single service, single revision live).
- No Slack/email deploy notifications (GitHub's own run status suffices).
- No auto-rollback beyond Cloud Run's "previous revision keeps serving on failed deploy."

## Open items to confirm during implementation

- **Project number** `1054388126708` — the setup script derives it from `gcloud projects describe`; confirm it matches before pasting the `WIF_PROVIDER` value into the workflow.
- **`setup-java` Temurin 25 availability** on GitHub runners — if the exact version isn't offered, fall back to Gradle's toolchain auto-provisioning.
