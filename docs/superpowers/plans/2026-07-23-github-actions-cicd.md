# GitHub Actions CI/CD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automate music-cat's build and deploy so a merge/push to `master` ships a new Cloud Run revision with no local machine involved, gated by tests, authenticating to GCP keylessly.

**Architecture:** Two GitHub Actions workflows. `ci.yml` runs backend tests + a frontend build on PRs. `deploy.yml` (on push to `master` + manual dispatch) re-runs tests, builds the container on an amd64 runner, and deploys to Cloud Run — authenticating via Workload Identity Federation (no stored key). Runtime config is injected from GitHub secrets, mirroring `deploy.sh` exactly.

**Tech Stack:** GitHub Actions, Workload Identity Federation, `google-github-actions/auth@v2` + `setup-gcloud@v2`, Docker (amd64 runner), Cloud Run, Artifact Registry, Gradle (Temurin 25), Node 22.12.0.

## Global Constraints

- **Personal account only:** every GCP/GitHub/Google operation uses `gibsonshamray@gmail.com` (GitHub user `alexshamrai`). NEVER the LoopMe work account, not even transiently. The local `gcloud` is currently on the work account, so GCP setup runs under a dedicated personal named config.
- **$0 guardrails preserved:** Artifact Registry keep-2-images cleanup policy, no Container Scanning, `--min-instances=0 --max-instances=1`, free `*.run.app` URL. No always-on cost added.
- **Deploy must match `deploy.sh` exactly:** `--allow-unauthenticated --min-instances=0 --max-instances=1 --cpu=1 --memory=1Gi --cpu-boost`, `--set-env-vars=SPRING_PROFILES_ACTIVE=cloud,SHEETS_SPREADSHEET_ID=…,MUSIC_CAT_USER=…,MUSIC_CAT_PASSWORD=…`, `--set-secrets=/secrets/google/credentials.json=sheets-sa-key:latest`.
- **WIF provider MUST carry an attribute condition** locking token exchange to `assertion.repository=='alexshamrai/music-cat'`. Without it, any repo on the internet can impersonate the deployer SA.
- **Fixed values:** project `music-cat-hosting` (number `1054388126708`, verify), region `europe-west1`, service `music-cat`, Artifact Registry repo `music-cat`, runtime SA `1054388126708-compute@developer.gserviceaccount.com`, deployer SA `gh-deployer@music-cat-hosting.iam.gserviceaccount.com`, WIF pool `github-pool`, provider `github-provider`.
- **Gradle wrapper is at repo root** (multi-module): run `./gradlew :backend:test` from root, never `cd backend`.
- **Keep `deploy.sh`** as the manual fallback — do not delete it.

## Dependency graph

- **Task 1** (GCP setup) and **Task 2** (GitHub secrets) are developer-run prerequisites. They can be done in parallel with Tasks 3–5.
- **Tasks 3, 4, 5** (create the workflow files + docs) are independent of Tasks 1–2 and can be done anytime.
- **Task 6** (land on master + verify the first automated deploy) requires Tasks 1, 2, 3, 4 complete.

## File structure

- Create: `.github/workflows/ci.yml` — PR gate (backend tests + frontend build).
- Create: `.github/workflows/deploy.yml` — build + deploy to Cloud Run on push to `master`.
- Modify: `README.md` — document automated deploys under "Live deployment".
- Modify: `CLAUDE.md` — note the CI/CD path in the deployment section.
- No application code, Dockerfile, or Cloud Run sizing changes.

---

### Task 1: One-time GCP setup — Workload Identity Federation (DEVELOPER-RUN, personal account)

This task is executed by the developer in a terminal, authenticated as `gibsonshamray@gmail.com`. An agent must NOT run these (they would run under the work account). It has no code test cycle; each step has an explicit verification.

**Files:** none (GCP-side resources only).

**Interfaces:**
- Produces: a WIF provider resource name string of the form
  `projects/1054388126708/locations/global/workloadIdentityPools/github-pool/providers/github-provider`
  — consumed verbatim by `deploy.yml`'s `WIF_PROVIDER` env in Task 4.
- Produces: deployer SA `gh-deployer@music-cat-hosting.iam.gserviceaccount.com` — consumed by `deploy.yml`'s `DEPLOYER_SA` env.

- [ ] **Step 1: Switch gcloud to the personal account (interactive)**

```bash
gcloud config configurations create music-cat 2>/dev/null || gcloud config configurations activate music-cat
gcloud auth login          # ← in the browser, pick gibsonshamray@gmail.com (NOT the LoopMe account)
gcloud config set account gibsonshamray@gmail.com
gcloud config set project music-cat-hosting
```

- [ ] **Step 2: Verify the active account is personal**

Run: `gcloud config list`
Expected: `account = gibsonshamray@gmail.com` and `project = music-cat-hosting`.
STOP if the account shows anything `@loopme.com`.

- [ ] **Step 3: Set shell variables and verify the project number**

```bash
PROJECT_ID=music-cat-hosting
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
echo "PROJECT_NUMBER=$PROJECT_NUMBER"      # expect 1054388126708
REPO=alexshamrai/music-cat
RUNTIME_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
DEPLOYER_SA="gh-deployer@${PROJECT_ID}.iam.gserviceaccount.com"
```

Expected: `PROJECT_NUMBER=1054388126708`. If it differs, use the printed value everywhere below and in Task 4's `WIF_PROVIDER`.

- [ ] **Step 4: Enable the WIF-related APIs**

```bash
gcloud services enable iamcredentials.googleapis.com sts.googleapis.com --project="$PROJECT_ID"
```

Run to verify: `gcloud services list --enabled --project="$PROJECT_ID" --filter="config.name:(iamcredentials.googleapis.com OR sts.googleapis.com)" --format="value(config.name)"`
Expected: both names listed.

- [ ] **Step 5: Create the deployer service account**

```bash
gcloud iam service-accounts create gh-deployer \
  --project="$PROJECT_ID" --display-name="GitHub Actions deployer"
```

Run to verify: `gcloud iam service-accounts describe "$DEPLOYER_SA" --project="$PROJECT_ID" --format="value(email)"`
Expected: `gh-deployer@music-cat-hosting.iam.gserviceaccount.com`.

- [ ] **Step 6: Grant the deployer the three roles it needs**

```bash
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$DEPLOYER_SA" --role="roles/run.admin"
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$DEPLOYER_SA" --role="roles/artifactregistry.writer"
# Lets the deployer deploy a revision that RUNS AS the runtime compute SA:
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --project="$PROJECT_ID" \
  --member="serviceAccount:$DEPLOYER_SA" --role="roles/iam.serviceAccountUser"
```

Run to verify: `gcloud projects get-iam-policy "$PROJECT_ID" --flatten="bindings[].members" --filter="bindings.members:$DEPLOYER_SA" --format="table(bindings.role)"`
Expected: rows for `roles/run.admin` and `roles/artifactregistry.writer`.

- [ ] **Step 7: Create the workload identity pool**

```bash
gcloud iam workload-identity-pools create github-pool \
  --project="$PROJECT_ID" --location=global --display-name="GitHub Actions pool"
```

Run to verify: `gcloud iam workload-identity-pools describe github-pool --project="$PROJECT_ID" --location=global --format="value(state)"`
Expected: `ACTIVE`.

- [ ] **Step 8: Create the GitHub OIDC provider, locked to the one repo**

```bash
gcloud iam workload-identity-pools providers create-oidc github-provider \
  --project="$PROJECT_ID" --location=global --workload-identity-pool=github-pool \
  --display-name="GitHub OIDC" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
  --attribute-condition="assertion.repository=='${REPO}'"
```

Run to verify: `gcloud iam workload-identity-pools providers describe github-provider --project="$PROJECT_ID" --location=global --workload-identity-pool=github-pool --format="value(attributeCondition)"`
Expected: `assertion.repository=='alexshamrai/music-cat'`.

- [ ] **Step 9: Allow only this repo's WIF principal to impersonate the deployer SA**

```bash
gcloud iam service-accounts add-iam-policy-binding "$DEPLOYER_SA" \
  --project="$PROJECT_ID" --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/attribute.repository/${REPO}"
```

Run to verify: `gcloud iam service-accounts get-iam-policy "$DEPLOYER_SA" --project="$PROJECT_ID" --format="json"` and confirm a binding for `roles/iam.workloadIdentityUser` whose member ends in `attribute.repository/alexshamrai/music-cat`.

- [ ] **Step 10: Print the WIF provider resource name for Task 4**

```bash
echo "projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/providers/github-provider"
```

Expected: `projects/1054388126708/locations/global/workloadIdentityPools/github-pool/providers/github-provider`.
Save this string — Task 4's `WIF_PROVIDER` must match it exactly.

---

### Task 2: Add GitHub Actions secrets (DEVELOPER-RUN, personal account)

Executed by the developer via the GitHub web UI (Settings → Secrets and variables → Actions → New repository secret) while signed in as `alexshamrai`, OR via `gh` if the CLI is authenticated to the personal account. No code test cycle.

**Files:** none (GitHub-side).

**Interfaces:**
- Produces: repo secrets `MUSIC_CAT_USER`, `MUSIC_CAT_PASSWORD`, `SHEETS_SPREADSHEET_ID` — consumed by `deploy.yml` in Task 4.

- [ ] **Step 1: Add the three secrets**

Web UI: add each of these three repository secrets:
- `MUSIC_CAT_USER` = the live Basic-auth username
- `MUSIC_CAT_PASSWORD` = the live Basic-auth password (never `admin`)
- `SHEETS_SPREADSHEET_ID` = `120QOqIDJG0iM0yG7C2Kus--gptc4EGKIsp7I8pTGoN4`

Or, if `gh` is authenticated as the personal account:

```bash
gh secret set MUSIC_CAT_USER --repo alexshamrai/music-cat
gh secret set MUSIC_CAT_PASSWORD --repo alexshamrai/music-cat
gh secret set SHEETS_SPREADSHEET_ID --repo alexshamrai/music-cat --body "120QOqIDJG0iM0yG7C2Kus--gptc4EGKIsp7I8pTGoN4"
```

(Reuse the current live values — source them from the running service if unknown: `gcloud run services describe music-cat --project music-cat-hosting --region europe-west1 --format=json` → `spec.template.spec.containers[0].env`.)

- [ ] **Step 2: Verify all three exist**

Web UI: confirm three secrets are listed.
Or: `gh secret list --repo alexshamrai/music-cat`
Expected: `MUSIC_CAT_USER`, `MUSIC_CAT_PASSWORD`, `SHEETS_SPREADSHEET_ID` all present.

---

### Task 3: Create the CI workflow (`ci.yml`)

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: nothing (no secrets, no GCP).
- Produces: a `pull_request` → `master` gate with two jobs (`backend`, `frontend`).

- [ ] **Step 1: Create the directory and the workflow file**

Create `.github/workflows/ci.yml` with exactly:

```yaml
name: CI

on:
  pull_request:
    branches: [master]

permissions:
  contents: read

jobs:
  backend:
    name: Backend tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
      - uses: gradle/actions/setup-gradle@v4
      - name: Run backend tests
        run: ./gradlew :backend:test --no-daemon

  frontend:
    name: Frontend build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - name: Install dependencies
        working-directory: frontend
        run: npm ci
      - name: Build (typecheck + bundle)
        working-directory: frontend
        run: npm run build
```

- [ ] **Step 2: Validate the workflow with actionlint**

Run: `actionlint .github/workflows/ci.yml`
Expected: no output, exit code 0 (any error means a syntax/expression problem — fix before committing).

- [ ] **Step 3: Commit (local only — do not push yet)**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add PR gate (backend tests + frontend build)"
```

---

### Task 4: Create the deploy workflow (`deploy.yml`)

**Files:**
- Create: `.github/workflows/deploy.yml`

**Interfaces:**
- Consumes: WIF provider resource name (Task 1 Step 10), deployer SA email (Task 1), secrets `MUSIC_CAT_USER`/`MUSIC_CAT_PASSWORD`/`SHEETS_SPREADSHEET_ID` (Task 2).
- Produces: an automated Cloud Run deploy on push to `master` and on manual dispatch.

- [ ] **Step 1: Create the workflow file**

Create `.github/workflows/deploy.yml` with exactly (if Task 1 Step 3 produced a project number other than `1054388126708`, substitute it in `WIF_PROVIDER`):

```yaml
name: Deploy

on:
  push:
    branches: [master]
  workflow_dispatch:

concurrency:
  group: deploy-master
  cancel-in-progress: false

permissions:
  contents: read

env:
  PROJECT: music-cat-hosting
  REGION: europe-west1
  SERVICE: music-cat
  AR_REPO: music-cat
  WIF_PROVIDER: projects/1054388126708/locations/global/workloadIdentityPools/github-pool/providers/github-provider
  DEPLOYER_SA: gh-deployer@music-cat-hosting.iam.gserviceaccount.com

jobs:
  test:
    name: Backend tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
      - uses: gradle/actions/setup-gradle@v4
      - name: Run backend tests
        run: ./gradlew :backend:test --no-daemon

  deploy:
    name: Build & deploy to Cloud Run
    needs: test
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write
    steps:
      - uses: actions/checkout@v4

      - name: Authenticate to Google Cloud (WIF)
        uses: google-github-actions/auth@v2
        with:
          project_id: ${{ env.PROJECT }}
          workload_identity_provider: ${{ env.WIF_PROVIDER }}
          service_account: ${{ env.DEPLOYER_SA }}

      - name: Set up gcloud
        uses: google-github-actions/setup-gcloud@v2

      - name: Configure Docker for Artifact Registry
        run: gcloud auth configure-docker ${{ env.REGION }}-docker.pkg.dev --quiet

      - name: Build and push image
        run: |
          IMAGE="${{ env.REGION }}-docker.pkg.dev/${{ env.PROJECT }}/${{ env.AR_REPO }}/app:${GITHUB_SHA::7}"
          docker build -t "$IMAGE" .
          docker push "$IMAGE"
          echo "IMAGE=$IMAGE" >> "$GITHUB_ENV"

      - name: Deploy to Cloud Run
        run: |
          gcloud run deploy "${{ env.SERVICE }}" \
            --project="${{ env.PROJECT }}" \
            --image="$IMAGE" \
            --region="${{ env.REGION }}" \
            --allow-unauthenticated \
            --min-instances=0 --max-instances=1 \
            --cpu=1 --memory=1Gi --cpu-boost \
            --set-env-vars="SPRING_PROFILES_ACTIVE=cloud,SHEETS_SPREADSHEET_ID=${{ secrets.SHEETS_SPREADSHEET_ID }},MUSIC_CAT_USER=${{ secrets.MUSIC_CAT_USER }},MUSIC_CAT_PASSWORD=${{ secrets.MUSIC_CAT_PASSWORD }}" \
            --set-secrets=/secrets/google/credentials.json=sheets-sa-key:latest \
            --quiet

      - name: Print service URL
        run: |
          gcloud run services describe "${{ env.SERVICE }}" \
            --project="${{ env.PROJECT }}" --region="${{ env.REGION }}" \
            --format="value(status.url)"
```

- [ ] **Step 2: Validate the workflow with actionlint**

Run: `actionlint .github/workflows/deploy.yml`
Expected: no output, exit code 0.

- [ ] **Step 3: Commit (local only — do not push yet)**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: add automated Cloud Run deploy on push to master (keyless WIF)"
```

---

### Task 5: Document the automated deploy path

**Files:**
- Modify: `README.md` (after the `deploy.sh` explanation paragraph ending "not Cloud Run IAM." — currently line 81)
- Modify: `CLAUDE.md` (deployment section)

**Interfaces:** none.

- [ ] **Step 1: Add an "Automated deploys" subsection to README.md**

Insert this block into `README.md` immediately after the paragraph that ends `not Cloud Run IAM.` (before the "Editing data by hand." paragraph):

```markdown

**Automated deploys (GitHub Actions).** Pushing/merging to `master` triggers
`.github/workflows/deploy.yml`: it runs the backend tests, builds the image on an
amd64 runner (no cross-build needed), and deploys a new Cloud Run revision —
mirroring `deploy.sh`'s flags. Pull requests run `.github/workflows/ci.yml`
(backend tests + frontend build) as a merge gate. GCP auth is keyless via
Workload Identity Federation (no service-account key stored in GitHub); the live
`MUSIC_CAT_USER` / `MUSIC_CAT_PASSWORD` / `SHEETS_SPREADSHEET_ID` values come from
repository secrets. `deploy.sh` remains available for manual/emergency local deploys.
```

- [ ] **Step 2: Add a CI/CD note to CLAUDE.md**

In `CLAUDE.md`, under the `### Key Commands` section, after the `# Deploy to Cloud Run` block, add:

```markdown

# Automated deploy: push/merge to master runs .github/workflows/deploy.yml
# (test → build → deploy via keyless WIF). PRs run .github/workflows/ci.yml.
# deploy.sh stays as the manual fallback.
```

- [ ] **Step 3: Commit (local only — do not push yet)**

```bash
git add README.md CLAUDE.md
git commit -m "docs: document GitHub Actions CI/CD deploy path"
```

---

### Task 6: Land on master and verify the first automated deploy

**Prerequisites:** Tasks 1, 2, 3, 4 complete. Pushing to `master` triggers `deploy.yml`, so this is where the pipeline runs for real. **Get the developer's explicit go-ahead before pushing** (standing "confirm before push" policy).

**Files:** none (uses existing commits).

**Interfaces:**
- Consumes: the committed workflow files (Tasks 3–4), the WIF trust path (Task 1), the secrets (Task 2).
- Produces: a new live Cloud Run revision deployed by GitHub Actions.

- [ ] **Step 1: Confirm prerequisites are done**

Verify Task 1 Step 2, Step 10 outputs and Task 2 Step 2 all succeeded. Confirm `deploy.yml`'s `WIF_PROVIDER`/`DEPLOYER_SA` match Task 1's outputs.

- [ ] **Step 2: Push master (personal SSH remote), with developer approval**

```bash
git push origin master
```

(Remote `origin` is `git@github.com-alexshamrai:alexshamrai/music-cat.git` — personal. This also uploads the two new workflow files.)

- [ ] **Step 3: Watch the deploy run**

Web UI: repo → Actions → the "Deploy" run for the just-pushed commit.
Or, if `gh` is authed personal: `gh run watch --repo alexshamrai/music-cat`
Expected: `test` job green, then `deploy` job green. The "Authenticate to Google Cloud (WIF)" step must succeed (proves the trust path). The final step prints the service URL.

- [ ] **Step 4: Verify a new revision was created by the pipeline**

Run (personal gcloud config):
```bash
gcloud run revisions list --service=music-cat --project=music-cat-hosting --region=europe-west1 --format="table(metadata.name, status.conditions[0].status, spec.containers[0].image)" --limit=3
```
Expected: the newest revision's image tag equals the pushed commit's short SHA.

- [ ] **Step 5: Verify config parity with deploy.sh**

Run:
```bash
gcloud run services describe music-cat --project=music-cat-hosting --region=europe-west1 \
  --format="value(spec.template.spec.containers[0].env[].name, spec.template.spec.containers[0].image)"
```
Expected: env includes `SPRING_PROFILES_ACTIVE`, `SHEETS_SPREADSHEET_ID`, `MUSIC_CAT_USER`, `MUSIC_CAT_PASSWORD`; and confirm the `sheets-sa-key` mount is present (`gcloud run services describe … --format=export | grep -A2 secretKeyRef`).

- [ ] **Step 6: Smoke-test the live URL**

Open the printed URL in a browser → expect an HTTP Basic auth prompt, then the app loads. (Cold start ~10–15s per the README.)

- [ ] **Step 7: Verify the free-tier cleanup policy still holds**

Run:
```bash
gcloud artifacts docker images list europe-west1-docker.pkg.dev/music-cat-hosting/music-cat/app --format="value(version)" | wc -l
```
Expected: ≤ 2 images retained (cleanup policy intact).

- [ ] **Step 8 (optional): Verify the PR gate**

Open a throwaway PR with a trivial change → confirm `ci.yml`'s `backend` and `frontend` jobs run and pass. Optionally introduce a deliberate TS error to confirm the `frontend` job fails, then discard the PR.

---

## Self-Review

**1. Spec coverage:**
- Keyless WIF auth → Task 1 (setup) + Task 4 (auth step). ✓
- Config via GitHub secrets → Task 2 + Task 4 deploy step. ✓
- `ci.yml` backend tests + frontend build → Task 3. ✓
- `deploy.yml` test → build → deploy on master + manual dispatch → Task 4. ✓
- Attribute condition locking to the repo → Task 1 Step 8. ✓
- `iam.serviceAccountUser` on runtime SA → Task 1 Step 6. ✓
- Deploy flags mirror `deploy.sh` → Task 4 Step 1 (verified in Task 6 Step 5). ✓
- Sequencing (setup+secrets before workflows land) → dependency graph + Task 6 prerequisites. ✓
- Verification plan (CI run, deploy run, config parity, guardrails) → Task 6 Steps 3–8. ✓
- `deploy.sh` kept → Global Constraints + Task 5 docs. ✓
- Personal-account-only → Global Constraints + Task 1 Steps 1–2. ✓
- Open items (project number, Temurin 25) → Task 1 Step 3 verifies the number; Task 3/4 use `java-version: '25'` with Gradle's `setup-gradle` cache (Gradle toolchain auto-provisions if the runner image lacks 25).

**2. Placeholder scan:** No TBD/TODO/"handle edge cases"/"similar to Task N". All file contents are complete and literal. ✓

**3. Type/name consistency:** `WIF_PROVIDER` string is identical between Task 1 Step 10 and Task 4 Step 1. `DEPLOYER_SA`, `github-pool`, `github-provider`, secret names, service/region/project all consistent across tasks. ✓
