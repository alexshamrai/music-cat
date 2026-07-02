#!/usr/bin/env bash
# Builds the app image and deploys it to Cloud Run. See task-list.md Task 17 and
# README.md's "Live deployment" section.
#
# Required environment variables (never hardcoded — set them before running):
#   MUSIC_CAT_USER          Basic auth username for the live service
#   MUSIC_CAT_PASSWORD      Basic auth password for the live service (not "admin")
#   SHEETS_SPREADSHEET_ID   The music-cat spreadsheet ID (Task 16)
set -euo pipefail

REGION=europe-west1
PROJECT=music-cat-hosting
SERVICE=music-cat

for var in MUSIC_CAT_USER MUSIC_CAT_PASSWORD SHEETS_SPREADSHEET_ID; do
  if [ -z "${!var:-}" ]; then
    echo "ERROR: $var is not set. Export it before running deploy.sh:" >&2
    echo "  export MUSIC_CAT_USER=... MUSIC_CAT_PASSWORD=... SHEETS_SPREADSHEET_ID=..." >&2
    exit 1
  fi
done

if [ "$MUSIC_CAT_USER" = "admin" ] && [ "$MUSIC_CAT_PASSWORD" = "admin" ]; then
  echo "ERROR: refusing to deploy with the default admin/admin credentials — the" >&2
  echo "Cloud Run URL is public. Set MUSIC_CAT_USER/MUSIC_CAT_PASSWORD to something else." >&2
  exit 1
fi

IMAGE="$REGION-docker.pkg.dev/$PROJECT/music-cat/app:$(git rev-parse --short HEAD)"

echo "Building $IMAGE for linux/amd64 (this Mac may be arm64) ..."
docker buildx build --platform linux/amd64 -t "$IMAGE" --push .

echo "Deploying $SERVICE to Cloud Run in $REGION ..."
gcloud run deploy "$SERVICE" \
  --project="$PROJECT" \
  --image="$IMAGE" \
  --region="$REGION" \
  --allow-unauthenticated \
  --min-instances=0 --max-instances=1 \
  --cpu=1 --memory=1Gi \
  --cpu-boost \
  --set-env-vars="SPRING_PROFILES_ACTIVE=cloud,SHEETS_SPREADSHEET_ID=$SHEETS_SPREADSHEET_ID,MUSIC_CAT_USER=$MUSIC_CAT_USER,MUSIC_CAT_PASSWORD=$MUSIC_CAT_PASSWORD" \
  --set-secrets=/secrets/google/credentials.json=sheets-sa-key:latest

echo "Deployed. Service URL:"
gcloud run services describe "$SERVICE" --project="$PROJECT" --region="$REGION" --format="value(status.url)"
