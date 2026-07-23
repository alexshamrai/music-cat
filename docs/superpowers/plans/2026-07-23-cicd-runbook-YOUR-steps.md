# CI/CD setup — your step-by-step runbook

Everything here runs under the **personal account `gibsonshamray@gmail.com`** — never the LoopMe work account.
The workflow files and docs are already written and committed locally (nothing pushed yet).
When you finish Parts A–C, do Part D and the first automated deploy runs.

---

## Part A — GCP Workload Identity Federation (one-time)

### A0. Switch gcloud to the personal account (interactive — pick personal in the browser)

```bash
gcloud config configurations create music-cat 2>/dev/null || gcloud config configurations activate music-cat
gcloud auth login          # ← a browser opens: choose gibsonshamray@gmail.com, NOT @loopme.com
gcloud config set account gibsonshamray@gmail.com
gcloud config set project music-cat-hosting
```

**Checkpoint — verify the account before doing anything else:**

```bash
gcloud config list
```

You must see `account = gibsonshamray@gmail.com` and `project = music-cat-hosting`.
**If it shows anything `@loopme.com`, STOP** and redo A0.

### A1. Run the whole setup (paste this block as-is after the checkpoint passes)

```bash
# --- variables ---
PROJECT_ID=music-cat-hosting
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
REPO=alexshamrai/music-cat
RUNTIME_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
DEPLOYER_SA="gh-deployer@${PROJECT_ID}.iam.gserviceaccount.com"
echo "PROJECT_NUMBER=$PROJECT_NUMBER   (expected 1054388126708)"

# --- enable APIs ---
gcloud services enable iamcredentials.googleapis.com sts.googleapis.com --project="$PROJECT_ID"

# --- deployer service account ---
gcloud iam service-accounts create gh-deployer \
  --project="$PROJECT_ID" --display-name="GitHub Actions deployer"

# --- grant it the 3 permissions it needs ---
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$DEPLOYER_SA" --role="roles/run.admin"
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$DEPLOYER_SA" --role="roles/artifactregistry.writer"
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --project="$PROJECT_ID" \
  --member="serviceAccount:$DEPLOYER_SA" --role="roles/iam.serviceAccountUser"

# --- workload identity pool + GitHub OIDC provider (locked to this ONE repo) ---
gcloud iam workload-identity-pools create github-pool \
  --project="$PROJECT_ID" --location=global --display-name="GitHub Actions pool"

gcloud iam workload-identity-pools providers create-oidc github-provider \
  --project="$PROJECT_ID" --location=global --workload-identity-pool=github-pool \
  --display-name="GitHub OIDC" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
  --attribute-condition="assertion.repository=='${REPO}'"

# --- let ONLY this repo impersonate the deployer SA ---
gcloud iam service-accounts add-iam-policy-binding "$DEPLOYER_SA" \
  --project="$PROJECT_ID" --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/attribute.repository/${REPO}"

# --- print the value that must match deploy.yml ---
echo "WIF_PROVIDER = projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/providers/github-provider"
```

### A2. Checkpoint

- The last line prints:
  `projects/1054388126708/locations/global/workloadIdentityPools/github-pool/providers/github-provider`
- This must equal the `WIF_PROVIDER:` line already in `.github/workflows/deploy.yml`.
- **If `PROJECT_NUMBER` was NOT `1054388126708`**, tell me — I'll update `deploy.yml` before we push.

---

## Part B — GitHub repository secrets (one-time)

Add three secrets at **github.com/alexshamrai/music-cat → Settings → Secrets and variables → Actions → New repository secret** (signed in as your personal `alexshamrai` account):

| Secret name | Value |
|---|---|
| `MUSIC_CAT_USER` | your live Basic-auth username |
| `MUSIC_CAT_PASSWORD` | your live Basic-auth password (never `admin`) |
| `SHEETS_SPREADSHEET_ID` | `120QOqIDJG0iM0yG7C2Kus--gptc4EGKIsp7I8pTGoN4` |

**Don't remember the user/password?** With gcloud on the personal account you can read the current live values:

```bash
gcloud run services describe music-cat --project music-cat-hosting --region europe-west1 \
  --format="json" | python3 -c "import json,sys; e={x['name']:x.get('value') for x in json.load(sys.stdin)['spec']['template']['spec']['containers'][0]['env']}; print('USER=',e.get('MUSIC_CAT_USER')); print('PASSWORD=',e.get('MUSIC_CAT_PASSWORD')); print('SHEET=',e.get('SHEETS_SPREADSHEET_ID'))"
```

**Checkpoint:** the repo's Actions secrets page lists all three names.

---

## Part C — Confirm before pushing

- [ ] `gcloud config list` shows `gibsonshamray@gmail.com` (Part A0 checkpoint).
- [ ] Part A finished with no red errors; `WIF_PROVIDER` matches `deploy.yml`.
- [ ] Three GitHub secrets exist (Part B checkpoint).

Do **not** push before A and B are done — the first deploy would fail at the auth step because the trust path wouldn't exist yet.

---

## Part D — Go live (first automated deploy)

Two options:

**Option 1 — I do it (recommended).** Tell me "Parts A & B are done, push it." With your go-ahead I'll `git push origin master`, then we watch the run and verify the new revision, config parity, and the keep-2-images guardrail together.

**Option 2 — You do it.**

```bash
git push origin master
```

Then watch **github.com/alexshamrai/music-cat → Actions → Deploy**. Expect the `test` job green, then the `deploy` job green (the "Authenticate to Google Cloud (WIF)" step succeeding proves the trust path). The final step prints the service URL — open it and confirm the Basic-auth prompt + app load (cold start ~10–15s).

---

## If something fails

- **Auth step fails** (`deploy` job, "Authenticate to Google Cloud") → the WIF binding or the repo lock is off. Re-check A1's provider `attribute-condition` and the `iam.workloadIdentityUser` member string both say `alexshamrai/music-cat`.
- **"permission denied on secret" / "cannot act as service account"** → re-run the `roles/iam.serviceAccountUser` binding in A1 (deployer must be able to act as the runtime compute SA). The runtime SA also needs `roles/secretmanager.secretAccessor` on `sheets-sa-key` (granted previously; if the app can't read Sheets after deploy, re-grant it).
- **`test` job fails** → tests are red on that commit; `deploy` won't run. Fix and re-push.
- **Anything else** → paste the failing step's log and I'll debug.
