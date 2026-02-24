#!/usr/bin/env python3
"""Music folder scanner - produces catalog.json from Genre/Artist/Album/*.mp3 structure."""

import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path


def natural_sort_key(s):
    """Sort strings with embedded numbers naturally (2 before 10)."""
    return [int(c) if c.isdigit() else c.lower() for c in re.split(r'(\d+)', s)]


def is_hidden(name):
    return name.startswith('.')


# Year-extraction patterns ordered by specificity
YEAR_PATTERNS = [
    # "01. 1963 - Please Please Me (Vinyl Rip, MFSL 1-101)"
    (r'^(\d{2,3})\.\s*(\d{4})\s*-\s*(.+)$', lambda m: (m.group(3).strip(), int(m.group(2)))),
    # "1962.Herbie Hancock - Takin' Off [320]"
    (r'^(\d{4})\.(.+?)(?:\s*\[\d+\])?\s*$', lambda m: (m.group(2).strip().lstrip('- '), int(m.group(1)))),
    # "NN-Title (Year)" or "NN Title (Year)" — e.g. "01-Queen (1973)"
    (r'^(\d{2,3})\s*[-\.]\s*(.+?)\s*\((\d{4})\)\s*$', lambda m: (m.group(2).strip(), int(m.group(3)))),
    # "Artist - Year - Title ..." — e.g. "Hinder - 2005 - Extreme Behavior (Deluxe Edition)"
    (r'^.+?\s*-\s*(\d{4})\s*-\s*(.+)$', lambda m: (m.group(2).strip(), int(m.group(1)))),
    # "Year - Title" — e.g. "1959 - Please Please Please"
    (r'^(\d{4})\s*-\s*(.+)$', lambda m: (m.group(2).strip(), int(m.group(1)))),
    # "Year_Title" — e.g. "2000_A New Day Yesterday"
    (r'^(\d{4})_(.+)$', lambda m: (m.group(2).strip(), int(m.group(1)))),
    # "Year-Title" (no spaces) — e.g. "2004-Out Of Myself"
    (r'^(\d{4})-(.+)$', lambda m: (m.group(2).strip(), int(m.group(1)))),
    # "Year Title" (space only, no separator) — e.g. "1982 To a finland station"
    (r'^(\d{4})\s+([A-Za-z].+)$', lambda m: (m.group(2).strip(), int(m.group(1)))),
    # "Title (Year)" at end — e.g. "Miles Davis - The Complete Birth Of The Cool (Remastered) (2019)"
    (r'^(.+)\((\d{4})\)\s*$', lambda m: (m.group(1).strip(), int(m.group(2)))),
    # "Title [Year]"
    (r'^(.+)\[(\d{4})\]\s*$', lambda m: (m.group(1).strip(), int(m.group(2)))),
]


def parse_album_name(folder_name):
    """Extract clean title and year from album folder name."""
    for pattern, extractor in YEAR_PATTERNS:
        m = re.match(pattern, folder_name)
        if m:
            title, year = extractor(m)
            # Clean trailing/leading dashes, dots, spaces
            title = re.sub(r'^[\s\-\.]+|[\s\-\.]+$', '', title)
            if 1920 <= year <= 2030:
                return title, year
    # No year found — return as-is
    return folder_name.strip(), None


def extract_artist_from_flat_album(folder_name):
    """Extract artist name from a folder like 'Artist - Year - Title' or 'Artist-Year-Title'."""
    # "Andrew Stockdale-2013-Keep Moving"
    m = re.match(r'^(.+?)\s*-\s*\d{4}\s*-\s*(.+)$', folder_name)
    if m:
        return m.group(1).strip(), folder_name
    # "Bridge To Mars - Bridge to Mars (2016)"
    m = re.match(r'^(.+?)\s*-\s*(.+)$', folder_name)
    if m:
        return m.group(1).strip(), folder_name
    # "Wynton Marsalis & Eric Clapton - Play The Blues..."
    # Already covered above
    return None, folder_name


NON_ALBUM_FOLDER_NAMES = {
    'text', 'texts', 'texts 2', 'box', 'covers', 'artwork', 'scans',
    'booklet', 'info', 'images', 'photos',
}


def is_non_album_folder(name):
    return name.lower() in NON_ALBUM_FOLDER_NAMES


def find_albums_recursive(path, max_depth=5):
    """Find all leaf directories that contain mp3 files, recursing through extra nesting."""
    if max_depth <= 0:
        return []
    albums = []
    try:
        entries = sorted(os.listdir(path))
    except PermissionError:
        return []

    mp3s = [e for e in entries if e.lower().endswith('.mp3') and not is_hidden(e)]
    subdirs = [e for e in entries if os.path.isdir(os.path.join(path, e)) and not is_hidden(e)]

    if mp3s:
        albums.append(path)

    for sd in subdirs:
        if is_non_album_folder(sd):
            continue
        albums.extend(find_albums_recursive(os.path.join(path, sd), max_depth - 1))

    return albums


def merge_multidisc(album_entries):
    """Merge albums that are disc splits (CD1/CD2, Disc 1/Disc 2) into single entries."""
    disc_pattern = re.compile(
        r'^(.*?)\s*[-_]?\s*(?:cd|disc|disk)\s*(\d+)\s*(.*)$', re.IGNORECASE
    )
    groups = {}  # base_key -> list of (disc_num, album_dict)
    standalone = []

    for album in album_entries:
        m = disc_pattern.match(album['title'])
        if m:
            base = (m.group(1).strip().rstrip('-_ ') + ' ' + m.group(3).strip()).strip()
            if not base:
                base = album['title']
                standalone.append(album)
                continue
            disc_num = int(m.group(2))
            key = base.lower()
            if key not in groups:
                groups[key] = []
            groups[key].append((disc_num, album))
        else:
            standalone.append(album)

    merged = []
    for base_key, discs in groups.items():
        if len(discs) == 1:
            # Single disc with disc marker — keep but clean title
            d = discs[0][1]
            standalone.append(d)
            continue
        discs.sort(key=lambda x: x[0])
        base_album = dict(discs[0][1])
        # Use the cleaned base name
        base_title = disc_pattern.match(discs[0][1]['title'])
        if base_title:
            clean = (base_title.group(1).strip().rstrip('-_ ') + ' ' + base_title.group(3).strip()).strip()
            base_album['title'] = clean if clean else discs[0][1]['title']
        # Merge songs from all discs
        all_songs = []
        years = []
        for _, d in discs:
            all_songs.extend(d['songs'])
            if d['year']:
                years.append(d['year'])
        base_album['songs'] = all_songs
        if years:
            base_album['year'] = years[0]
        merged.append(base_album)

    return standalone + merged


def scan_music(root_path):
    root = Path(root_path)
    if not root.is_dir():
        print(f"ERROR: {root_path} is not a valid directory")
        sys.exit(1)

    warnings = []
    catalog = []
    total_artists = 0
    total_albums = 0
    total_tracks = 0

    genre_dirs = sorted(
        [d for d in os.listdir(root) if os.path.isdir(root / d) and not is_hidden(d)]
    )

    for genre_name in genre_dirs:
        genre_path = root / genre_name
        genre_entry = {'genre': genre_name, 'artists': []}
        artists_map = {}  # artist_name -> list of album dicts

        entries = sorted(os.listdir(genre_path))
        subdirs = [e for e in entries if os.path.isdir(genre_path / e) and not is_hidden(e)]
        files = [e for e in entries if os.path.isfile(genre_path / e) and not is_hidden(e)]

        # Warn about files at genre level
        for f in files:
            warnings.append(f"Skipped file at genre level: {genre_name}/{f}")

        for artist_or_item in subdirs:
            item_path = genre_path / artist_or_item

            if is_non_album_folder(artist_or_item):
                warnings.append(f"Skipped non-album folder at artist level: {genre_name}/{artist_or_item}")
                continue

            # Check: does this dir contain mp3 files directly? (album at genre level)
            item_entries = os.listdir(item_path)
            has_mp3_direct = any(
                f.lower().endswith('.mp3') and not is_hidden(f)
                for f in item_entries if os.path.isfile(item_path / f)
            )
            has_subdirs = any(
                os.path.isdir(item_path / d) and not is_hidden(d)
                for d in item_entries
            )

            if has_mp3_direct and not has_subdirs:
                # Flat album at genre level — extract artist from folder name
                artist_name, _ = extract_artist_from_flat_album(artist_or_item)
                if not artist_name:
                    artist_name = artist_or_item
                album_title, year = parse_album_name(artist_or_item)
                # If artist was extracted, try to clean album title too
                if artist_name != artist_or_item:
                    # Remove artist prefix from album title
                    cleaned = re.sub(re.escape(artist_name) + r'\s*[-–]\s*', '', album_title, count=1)
                    if cleaned and cleaned != album_title:
                        album_title = cleaned.strip()

                mp3s = sorted(
                    [f for f in item_entries if f.lower().endswith('.mp3') and not is_hidden(f)],
                    key=natural_sort_key
                )
                non_mp3 = [
                    f for f in item_entries
                    if os.path.isfile(item_path / f) and not f.lower().endswith('.mp3') and not is_hidden(f)
                ]
                for f in non_mp3:
                    warnings.append(f"Skipped non-mp3 file: {genre_name}/{artist_or_item}/{f}")

                if mp3s:
                    album_entry = {'title': album_title, 'year': year, 'songs': mp3s}
                    if artist_name not in artists_map:
                        artists_map[artist_name] = []
                    artists_map[artist_name].append(album_entry)

                warnings.append(f"Album at genre level (no artist folder): {genre_name}/{artist_or_item} -> artist: '{artist_name}'")
                continue

            # Standard case: this is an artist directory
            artist_name = artist_or_item

            # Find all album dirs (handles extra nesting)
            album_paths = find_albums_recursive(str(item_path), max_depth=5)

            if not album_paths:
                # Check for non-album content
                item_files = [
                    f for f in item_entries if os.path.isfile(item_path / f) and not is_hidden(f)
                ]
                if item_files or item_entries:
                    warnings.append(f"Artist with no albums found: {genre_name}/{artist_name}")
                continue

            # Warn about files directly in artist dir
            artist_files = [
                f for f in item_entries
                if os.path.isfile(item_path / f) and not is_hidden(f)
            ]
            for f in artist_files:
                warnings.append(f"Skipped file at artist level: {genre_name}/{artist_name}/{f}")

            artist_albums = []
            for album_path_str in album_paths:
                album_path = Path(album_path_str)
                album_folder = album_path.name
                album_title, year = parse_album_name(album_folder)

                album_entries_list = os.listdir(album_path)
                mp3s = sorted(
                    [f for f in album_entries_list if f.lower().endswith('.mp3') and not is_hidden(f)],
                    key=natural_sort_key
                )
                non_mp3_files = [
                    f for f in album_entries_list
                    if os.path.isfile(album_path / f) and not f.lower().endswith('.mp3') and not is_hidden(f)
                ]
                non_album_subdirs = [
                    d for d in album_entries_list
                    if os.path.isdir(album_path / d) and not is_hidden(d)
                ]

                for f in non_mp3_files:
                    rel = os.path.relpath(album_path / f, root)
                    warnings.append(f"Skipped non-mp3 file: {rel}")
                for d in non_album_subdirs:
                    # Only warn if it wasn't already recursed into
                    sub = album_path / d
                    if not any(str(sub) in ap for ap in album_paths if ap != album_path_str):
                        rel = os.path.relpath(sub, root)
                        warnings.append(f"Skipped subfolder in album: {rel}")

                if not mp3s:
                    rel = os.path.relpath(album_path, root)
                    warnings.append(f"Album with no mp3 files: {rel}")
                    continue

                artist_albums.append({
                    'title': album_title,
                    'year': year,
                    'songs': mp3s,
                })

            # Merge multi-disc albums
            artist_albums = merge_multidisc(artist_albums)

            # Sort albums by year then title
            artist_albums.sort(key=lambda a: (a['year'] or 9999, natural_sort_key(a['title'])))

            if artist_albums:
                if artist_name not in artists_map:
                    artists_map[artist_name] = []
                artists_map[artist_name].extend(artist_albums)

        # Build artist entries from map
        for artist_name in sorted(artists_map.keys(), key=natural_sort_key):
            albums = artists_map[artist_name]
            albums.sort(key=lambda a: (a['year'] or 9999, natural_sort_key(a['title'])))
            track_count = sum(len(a['songs']) for a in albums)
            genre_entry['artists'].append({
                'name': artist_name,
                'albums': albums,
            })
            total_artists += 1
            total_albums += len(albums)
            total_tracks += track_count

        if genre_entry['artists']:
            catalog.append(genre_entry)

    result = {
        'scannedAt': datetime.now(timezone.utc).isoformat(),
        'rootPath': str(root.resolve()),
        'stats': {
            'totalGenres': len(catalog),
            'totalArtists': total_artists,
            'totalAlbums': total_albums,
            'totalTracks': total_tracks,
        },
        'warnings': warnings,
        'catalog': catalog,
    }

    return result


def main():
    music_path = sys.argv[1] if len(sys.argv) > 1 else '/Volumes/HP Desktop Drive/Music'
    print(f"Scanning: {music_path}")
    print("=" * 60)

    result = scan_music(music_path)

    output_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'catalog.json')
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    stats = result['stats']
    print(f"Genres:   {stats['totalGenres']}")
    print(f"Artists:  {stats['totalArtists']}")
    print(f"Albums:   {stats['totalAlbums']}")
    print(f"Tracks:   {stats['totalTracks']}")
    print(f"Warnings: {len(result['warnings'])}")
    print("=" * 60)

    if result['warnings']:
        print("\nWarnings:")
        for w in result['warnings']:
            print(f"  - {w}")

    print(f"\nOutput written to: {output_file}")


if __name__ == '__main__':
    main()
