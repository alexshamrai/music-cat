# Task 7: Browse, Artists & Album Detail Pages

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the Browse, Artist, and Album pages with shared reusable components for the music catalog frontend.

**Architecture:** 6 shared components (StarRating, FavoriteToggle, TagBadge, FilterBar, AlbumCard, ArtistCard) used across 5 pages (BrowsePage, ArtistListPage, ArtistDetailPage, AlbumDetailPage, AlbumListPage). All data fetching via existing TanStack Query hooks; mutations invalidate caches automatically.

**Tech Stack:** React 19, TypeScript, Tailwind CSS 4, Lucide React, TanStack Query v5, React Router 7

---

### Task 1: Shared Components — StarRating, FavoriteToggle, TagBadge

**Files:**
- Create: `frontend/src/components/StarRating.tsx`
- Create: `frontend/src/components/FavoriteToggle.tsx`
- Create: `frontend/src/components/TagBadge.tsx`

**StarRating:** Props: `grade: number | null`, `onChange?: (grade: number) => void`, `readonly?: boolean`. 5 star icons from lucide (Star). Filled yellow for active, outline for inactive. Clickable when not readonly. Shows "Unrated" when null and readonly.

**FavoriteToggle:** Props: `isFavorite: boolean`, `onToggle: () => void`. Heart icon from lucide. Filled red when favorited, outline gray when not.

**TagBadge:** Props: `tag: string`, `onRemove?: () => void`, `onClick?: () => void`. Rounded pill bg-gray-100. Small X button if onRemove. Clickable cursor if onClick.

**Commit:** `feat: add StarRating, FavoriteToggle, TagBadge components`

---

### Task 2: Shared Components — AlbumCard, ArtistCard

**Files:**
- Create: `frontend/src/components/AlbumCard.tsx`
- Create: `frontend/src/components/ArtistCard.tsx`

**AlbumCard:** Props: `album: AlbumSummary`. Link title to `/albums/:id`. Show artist name, year, song count. Readonly StarRating. FavoriteToggle (calls useToggleAlbumFavorite). TagBadges. Card: white bg, rounded-lg, border, p-4.

**ArtistCard:** Props: `artist: Artist`. Link name to `/artists/:id`. Genre badge, subgenre, album count. FavoriteToggle (calls useToggleArtistFavorite). TagBadges.

**Commit:** `feat: add AlbumCard and ArtistCard components`

---

### Task 3: Shared Component — FilterBar

**Files:**
- Create: `frontend/src/components/FilterBar.tsx`

Props: `filters: AlbumFilterParams`, `onChange: (filters: AlbumFilterParams) => void`. Genre dropdown (populated from useBrowseGenres). Min grade dropdown (1-5). Favorite only checkbox. Tag text input (Enter to add). Clear all button. Compact horizontal layout.

**Commit:** `feat: add FilterBar component`

---

### Task 4: BrowsePage

**Files:**
- Modify: `frontend/src/pages/BrowsePage.tsx`

Fetch genres via useBrowseGenres. Grid of genre cards (genre name, artist count, album count). Click genre → expand inline with useArtistsByGenre showing artist list. Click artist → navigate to `/artists/:id`. Accordion-style: one genre expanded at a time.

**Commit:** `feat: implement BrowsePage with genre drill-down`

---

### Task 5: ArtistListPage and ArtistDetailPage

**Files:**
- Modify: `frontend/src/pages/ArtistListPage.tsx`
- Modify: `frontend/src/pages/ArtistDetailPage.tsx`

**ArtistListPage:** Genre dropdown filter, favorite filter, tag filter. Grid of ArtistCards.

**ArtistDetailPage:** useArtist(id) + useAlbums({artistId}). Header: name, genre, subgenre. FavoriteToggle. Tag management (existing tags as removable TagBadges + text input to add). Grid of AlbumCards for artist's albums. Back link.

**Commit:** `feat: implement ArtistListPage and ArtistDetailPage`

---

### Task 6: AlbumListPage and AlbumDetailPage

**Files:**
- Modify: `frontend/src/pages/AlbumListPage.tsx`
- Modify: `frontend/src/pages/AlbumDetailPage.tsx`

**AlbumListPage:** Full FilterBar. Grid of AlbumCards. Filters stored in component state.

**AlbumDetailPage:** useAlbum(id). Header: title, year, artist link, genre. Clickable StarRating (calls useSetAlbumGrade). FavoriteToggle. Tag management. Song table: track#, title, disc# (if >1). Back link.

**Commit:** `feat: implement AlbumListPage and AlbumDetailPage`

---

### Task 7: Verify and Polish

Run `cd frontend && npm run dev`. Verify all pages render, navigation works, mutations update UI. Fix any TypeScript or runtime errors.

**Commit:** `feat: Task 7 complete — Browse, Artists & Album Detail pages`
