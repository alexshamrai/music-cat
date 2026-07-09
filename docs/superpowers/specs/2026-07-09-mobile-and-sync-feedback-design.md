# Mobile responsive shell + Google Sheets sync feedback

Date: 2026-07-09
Branch: `feature/mobile-design`

## Problem

1. **Layout is desktop-only on mobile.** `Layout.tsx` renders a permanent `w-60`
   sidebar in a horizontal flex with no responsive breakpoints. On a phone the
   sidebar eats 240px of width and cannot be collapsed; every page also hard-codes
   `p-8` and the song table in `AlbumDetailPage` forces horizontal page scroll.
2. **Mutations give no feedback.** The backend pushes to Google Sheets
   synchronously after every mutating commit, so a rating/tag/favorite save can
   take seconds. The UI currently only invalidates queries silently — the user
   has no idea a save is in flight or finished.

## Part A — Responsive shell

Switch breakpoint: **`lg` (1024px)**. At/above `lg`, layout is unchanged. Below
`lg`, the sidebar becomes a slide-in drawer.

- **`Layout.tsx`**: holds `drawerOpen` state.
  - `>= lg`: static `w-60` sidebar beside `<main>` (current behavior).
  - `< lg`: a slim top bar (logo + hamburger button). Hamburger opens the sidebar
    as a `fixed` drawer over a dimmed scrim (`bg-black/40`). Drawer closes on link
    tap, scrim tap, or `Escape`. Body scroll locks while open (`overflow-hidden`
    on `<html>`/`document.body`).
- **`Sidebar.tsx`**: gains optional `onNavigate?: () => void` called on link click
  (closes the drawer). Same link list serves both static and drawer renders — no
  duplication.
- **`PageContainer.tsx`** (new): shared wrapper applying `p-4 sm:p-6 lg:p-8`.
  Replace the bare `<div className="p-8">` page wrappers with it so padding lives
  in one place.
- **`AlbumDetailPage`**: wrap the song `<table>` in `overflow-x-auto` so it scrolls
  inside its own box.
- Grids already responsive (`grid-cols-1 sm:grid-cols-2 …`) — left as-is.

Z-index order: drawer/scrim above content; existing TagsPage modal is `z-50`, so
drawer uses `z-40` scrim / `z-50` panel and toast uses `z-[60]` to sit above both.

## Part B — Sheets-sync toast

Driven globally off TanStack Query's `MutationCache` — one wiring covers all 12
mutation hooks; the hooks themselves are untouched.

- **`toastStore.ts`** (new): dependency-free external store compatible with
  `useSyncExternalStore`. API: `subscribe`, `getSnapshot`, and imperative
  `start(id)`, `succeed(id)`, `fail(id, message)`. Keyed by `mutation.mutationId`
  so concurrent saves stack. Owns auto-dismiss timers (success ~2.5s, error ~6s).
- **`Toaster.tsx`** (new): fixed bottom-right stack, `role="status"
  aria-live="polite"`. States:
  - loading: spinner (`Loader2` + `animate-spin`) + "Saving to Google Sheets…"
  - success: green check (`CheckCircle2`) + "Saved"
  - error: red (`AlertCircle`) + "Couldn't save — <reason>" + manual close (X)
- **`App.tsx`**: `QueryClient` gets a `MutationCache` whose global
  `onMutate`/`onSuccess`/`onError` call the store. `<Toaster/>` renders at app root.
  Per-hook `onSuccess` invalidations continue to fire independently.

Error reason is derived from the axios error (response body message, else status
text, else generic).

## Data flow

mutation → `onMutate` shows loading toast → backend commit + Sheets push (the slow
seconds) → resolve → `onSuccess` flips toast to "Saved" and per-hook invalidation
refetches → UI updates. Failure → `onError` flips to error toast.

## Verification & deploy

- `cd frontend && npm run build` (tsc + vite) green; `./gradlew bootJar` green.
- Drive real app with Playwright at 390×844: drawer open/close, full-width content,
  no horizontal overflow, rating change shows "Saving to Google Sheets…" → "Saved".
- Deploy via `deploy.sh` authenticated as **gibsonshamray@gmail.com** (never the
  LoopMe work account). Verify active gcloud account/project before pushing.

## Out of scope

No new npm dependencies. No unrelated refactoring. Backend unchanged (the sync
already happens server-side; this only surfaces its timing in the UI).
