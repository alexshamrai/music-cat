# Edit Albums & Songs from the UI — Design

**Date:** 2026-07-09
**Branch:** `feature/edit-from-ui` (rebased onto `master` @ `525db8a`)
**Status:** Approved — implementing

## Goal

Let the user, from the album detail page, **rename an album** (title + year) and **add / rename / delete songs** within it. Editing uses an **edit-mode toggle**: click *Edit*, make all changes, click *Save* once (or *Cancel*).

## UUID decision — NOT adding UUIDs

The Google Sheet keeps its current 3-tab, name-keyed schema (Artist→`name`, Album→`(artist,title)`, Song→matched to album by `(artist,albumTitle)`). No ID column, no schema change.

Rationale: the app is the **sole writer** and re-pushes all three tabs from one consistent DB snapshot on every structural change, so app-driven renames/deletes/adds never orphan child rows and round-trip correctly through a `sync/pull`. UUIDs would only harden against **manual hand-edits of the sheet** and **same-name collisions** — real but pre-existing, orthogonal concerns whose proper fix (schema/PK change + reader/mapper rewrite + one-time backfill of ~34K live rows) is a separate hardening project, not part of this feature.

The one collision reachable by this feature — renaming an album to a title another album by the same artist already has — is closed with a cheap **backend validation guard** instead of UUIDs.

## Backend — single atomic batch endpoint

`PUT /api/albums/{id}/edit` (state-changing → requires `X-Requested-With`, like all mutations).

Request DTO:

```
AlbumEditDto {
  @Size(min = 1) String title;        // album title, non-blank
  Integer year;                        // nullable
  @Valid List<SongEditInput> songs;    // desired final song set
}
SongEditInput {
  Long id;              // null → new song; non-null → existing song to keep/rename
  @NotBlank String title;
}
```

`AlbumService.edit(Long id, AlbumEditDto dto)` — one `@Transactional` method, reconciles against the album loaded with its songs:

1. **Album fields:** set `title` / `year`. Collision guard: if `title` changed and another album (different id) by the same artist already has it → `NoMatchException`/400. Extract guard to a shared helper and apply it to the existing `AlbumService.update` too (same latent bug).
2. **Songs reconcile** (build a map of existing songs by id):
   - payload song with `id` present in this album → set its `title` (track/disc **preserved** from DB; track/disc editing is out of scope).
   - payload song with `id` **not** in this album → 400 "album changed, please reload" (fail loud, never corrupt — covers the rare cold-start-mid-edit id race).
   - payload song with `id == null` → new `SongEntity`, `disc = 1`, `track = max(existing track) + 1` (0 songs → track 1). Guarantees no `(disc,track)` collision; needs no numbering UI.
   - existing song whose id is **absent** from payload → removed from `album.getSongs()` → deleted via `orphanRemoval`.
3. Save album; publish exactly **one** `CatalogChangedEvent(true)` → one structural push → all 3 tabs rewritten consistently.

Returns the full `AlbumDto` (with the reconciled songs).

No Flyway/entity schema change. No `SongController` — songs stay album-scoped by the existing convention.

**Known simplification:** a new song always lands on disc 1 with the next track number. For a multi-disc album that can produce odd numbering; acceptable because track/disc editing is explicitly out of scope (natural follow-up).

## Frontend — edit mode on `AlbumDetailPage`

- **Edit** button toggles `isEditing`. On enter, seed a local **draft** (deep copy) `{ title, year, songs: [{ id|null, title, trackNumber, discNumber }] }`.
- Edit mode renders: album title `<input>`, year `<input>`, each song row as `[title input] 🗑`, and a `[ + Add song ]` row appending a blank `{ id: null, title: '' }`.
- **Save** → `useEditAlbum` mutation → `PUT /albums/{id}/edit` with `{ title, year, songs: draft.songs.map(s => ({ id: s.id ?? null, title: s.title })) }`. On success: invalidate `['albums']`, `['albums', id]`, `['stats']`; exit edit mode. Save disabled while album title or any song title is blank.
- **Cancel** discards the draft and exits edit mode.
- View mode is unchanged from today. The existing `MutationCache` shows "Saving to Google Sheets… → Saved" automatically; a 400 collision surfaces as the toast error.
- New code: `editAlbum()` in `api/client.ts`, `useEditAlbum` in `hooks/useAlbums.ts`, `AlbumEditDto`/`SongEditInput` in `types/index.ts`, edit UI in `pages/AlbumDetailPage.tsx`.

## Testing & verification

- **Backend (JUnit + MockMvc + security-test):** service reconcile — rename song, add song, delete song, album title/year change, collision → 400, foreign song id → 400, empty-album add → track 1, exactly-one `CatalogChangedEvent`. Controller — 200 happy, 400 validation (blank title), 400 collision, 404 unknown album, `X-Requested-With` enforced. A sheet round-trip test mirroring existing sync tests (edit → push → Songs tab reflects reconcile).
- **E2E:** Playwright pass against the running app (auth via `Authorization: Basic` header, per project convention): enter edit mode, rename album, rename a song, add a song, delete a song, Save, confirm persistence after refetch.

## Branch & deploy

1. Branch already fast-forwarded onto `master` (done).
2. Implement (TDD, backend-first) → backend tests green → local verify.
3. Adversarial code-review pass; fix findings.
4. Merge to `master`; deploy via `./deploy.sh` to Cloud Run (gcloud authed as `gibsonshamray@gmail.com`).
   - Deploy needs `MUSIC_CAT_USER`, `MUSIC_CAT_PASSWORD`, `SHEETS_SPREADSHEET_ID` supplied at deploy time.
5. Post-deploy smoke test against the live URL.
