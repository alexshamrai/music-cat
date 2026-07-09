import { useEffect, useRef, useState } from 'react';
import { useParams, Link } from 'react-router';
import { ArrowLeft, Pencil, Plus, Trash2 } from 'lucide-react';
import {
  useAlbum,
  useSetAlbumGrade,
  useToggleAlbumFavorite,
  useSetAlbumTags,
  useEditAlbum,
} from '../hooks/useAlbums';
import StarRating from '../components/StarRating';
import FavoriteToggle from '../components/FavoriteToggle';
import TagBadge from '../components/TagBadge';
import type { Album, Song } from '../types';

type DraftSong = { key: string; id: number | null; title: string };
type Draft = { title: string; year: string; songs: DraftSong[] };

const yearIsValid = (y: string) => y.trim() === '' || /^\d{1,4}$/.test(y.trim());

// Canonical song ordering (disc then track), shared by view and edit modes.
const bySong = (a: Song, b: Song) =>
  (a.discNumber ?? 1) - (b.discNumber ?? 1) || a.trackNumber - b.trackNumber;

export default function AlbumDetailPage() {
  const { id } = useParams<{ id: string }>();
  const albumId = Number(id);
  const { data: album, isLoading, isError } = useAlbum(albumId);
  const setGrade = useSetAlbumGrade();
  const toggleFav = useToggleAlbumFavorite();
  const setTags = useSetAlbumTags();
  const editAlbum = useEditAlbum();
  const [tagInput, setTagInput] = useState('');

  const [draft, setDraft] = useState<Draft | null>(null);
  const newKeyCounter = useRef(0);

  // Discard any in-progress edit when navigating to a different album — React Router
  // reuses this component instance on param-only navigation, so the draft would
  // otherwise leak onto (and be submitted against) the newly viewed album.
  useEffect(() => {
    setDraft(null);
  }, [albumId]);

  const addTag = () => {
    const t = tagInput.trim();
    if (!t || !album) return;
    if (!album.tags.includes(t)) {
      setTags.mutate({ id: albumId, tags: [...album.tags, t] });
    }
    setTagInput('');
  };

  const removeTag = (tag: string) => {
    if (!album) return;
    setTags.mutate({ id: albumId, tags: album.tags.filter((t) => t !== tag) });
  };

  const startEdit = (a: Album) => {
    const songs = [...a.songs]
      .sort(bySong)
      .map((s) => ({ key: `s-${s.id}`, id: s.id, title: s.title }));
    setDraft({ title: a.title, year: a.year != null ? String(a.year) : '', songs });
  };

  const cancelEdit = () => setDraft(null);

  const updateSongTitle = (key: string, title: string) =>
    setDraft((d) => (d ? { ...d, songs: d.songs.map((s) => (s.key === key ? { ...s, title } : s)) } : d));

  const removeSong = (key: string) =>
    setDraft((d) => (d ? { ...d, songs: d.songs.filter((s) => s.key !== key) } : d));

  const addSong = () =>
    setDraft((d) =>
      d ? { ...d, songs: [...d.songs, { key: `new-${newKeyCounter.current++}`, id: null, title: '' }] } : d,
    );

  const canSave =
    !!draft &&
    draft.title.trim() !== '' &&
    yearIsValid(draft.year) &&
    draft.songs.every((s) => s.title.trim() !== '');

  const saveEdit = () => {
    if (!draft || !canSave) return;
    editAlbum.mutate(
      {
        id: albumId,
        dto: {
          title: draft.title.trim(),
          year: draft.year.trim() === '' ? null : Number(draft.year),
          songs: draft.songs.map((s) => ({ id: s.id, title: s.title.trim() })),
        },
      },
      { onSuccess: () => setDraft(null) },
    );
  };

  if (isLoading) {
    return (
      <div className="p-4 sm:p-6 lg:p-8">
        <div className="text-gray-500">Loading album...</div>
      </div>
    );
  }

  if (isError || !album) {
    return (
      <div className="p-4 sm:p-6 lg:p-8">
        <div className="text-red-500">Album not found.</div>
      </div>
    );
  }

  const isEditing = draft !== null;
  const hasMultipleDiscs = album.songs.some((s) => s.discNumber && s.discNumber > 1);
  const sortedSongs = [...album.songs].sort(bySong);

  return (
    <div className="p-4 sm:p-6 lg:p-8">
      <Link to="/albums" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4">
        <ArrowLeft size={14} />
        Back to albums
      </Link>

      <div className="bg-white rounded-lg border border-gray-200 p-6 mb-6">
        {isEditing ? (
          <div className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Album title</label>
              <input
                type="text"
                aria-label="Album title"
                value={draft.title}
                onChange={(e) => setDraft((d) => (d ? { ...d, title: e.target.value } : d))}
                className="w-full text-lg font-semibold text-gray-900 border border-gray-300 rounded px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Year</label>
              <input
                type="text"
                inputMode="numeric"
                aria-label="Album year"
                value={draft.year}
                onChange={(e) => setDraft((d) => (d ? { ...d, year: e.target.value } : d))}
                placeholder="—"
                className={`w-28 text-sm text-gray-900 border rounded px-3 py-1.5 focus:outline-none focus:ring-2 ${
                  yearIsValid(draft.year)
                    ? 'border-gray-300 focus:ring-blue-500'
                    : 'border-red-400 focus:ring-red-500'
                }`}
              />
              {!yearIsValid(draft.year) && <span className="ml-2 text-xs text-red-500">Enter a valid year</span>}
            </div>
          </div>
        ) : (
          <div className="flex items-start justify-between">
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-2xl font-bold text-gray-900">{album.title}</h2>
                <button
                  onClick={() => startEdit(album)}
                  aria-label="Edit album"
                  className="text-gray-400 hover:text-gray-700"
                >
                  <Pencil size={16} />
                </button>
              </div>
              <div className="flex items-center gap-2 mt-1">
                <Link to={`/artists/${album.artist.id}`} className="text-sm text-blue-600 hover:text-blue-800">
                  {album.artist.name}
                </Link>
                <span className="text-sm text-gray-400">{album.artist.genre}</span>
                {album.year && <span className="text-sm text-gray-400">&middot; {album.year}</span>}
              </div>
            </div>
            <FavoriteToggle isFavorite={album.isFavorite} onToggle={() => toggleFav.mutate(albumId)} />
          </div>
        )}

        {!isEditing && (
          <>
            <div className="mt-4">
              <p className="text-xs font-medium text-gray-500 mb-1">Rating</p>
              <StarRating grade={album.grade ?? null} onChange={(grade) => setGrade.mutate({ id: albumId, grade })} />
            </div>

            <div className="mt-4">
              <p className="text-xs font-medium text-gray-500 mb-2">Tags</p>
              <div className="flex flex-wrap items-center gap-1.5">
                {album.tags.map((t) => (
                  <TagBadge key={t} tag={t} onRemove={() => removeTag(t)} />
                ))}
                <input
                  type="text"
                  value={tagInput}
                  onChange={(e) => setTagInput(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && addTag()}
                  placeholder="Add tag..."
                  className="text-xs border border-gray-200 rounded px-2 py-1 w-24"
                />
              </div>
            </div>
          </>
        )}
      </div>

      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-gray-900">
          Songs{' '}
          <span className="text-sm font-normal text-gray-400">
            ({isEditing ? draft.songs.length : album.songs.length})
          </span>
        </h3>
        {isEditing && (
          <div className="flex items-center gap-2">
            <button
              onClick={cancelEdit}
              disabled={editAlbum.isPending}
              className="text-sm px-3 py-1.5 rounded border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              onClick={saveEdit}
              disabled={!canSave || editAlbum.isPending}
              className="text-sm px-3 py-1.5 rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {editAlbum.isPending ? 'Saving…' : 'Save'}
            </button>
          </div>
        )}
      </div>

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          {isEditing ? (
            <div className="divide-y divide-gray-50">
              {draft.songs.map((song, i) => (
                <div key={song.key} className="flex items-center gap-3 px-4 py-2">
                  <span className="w-8 text-right text-gray-400 text-sm">{i + 1}</span>
                  <input
                    type="text"
                    aria-label={`Song ${i + 1} title`}
                    value={song.title}
                    onChange={(e) => updateSongTitle(song.key, e.target.value)}
                    placeholder="Song title"
                    className={`flex-1 text-sm text-gray-800 border rounded px-2 py-1 focus:outline-none focus:ring-2 ${
                      song.title.trim() === ''
                        ? 'border-red-400 focus:ring-red-500'
                        : 'border-gray-300 focus:ring-blue-500'
                    }`}
                  />
                  <button
                    onClick={() => removeSong(song.key)}
                    aria-label="Remove song"
                    className="text-gray-400 hover:text-red-600"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              ))}
              <button
                onClick={addSong}
                className="flex items-center gap-1.5 px-4 py-2.5 w-full text-sm text-blue-600 hover:bg-blue-50"
              >
                <Plus size={16} />
                Add song
              </button>
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 text-left text-xs text-gray-500 uppercase tracking-wider">
                  <th className="px-4 py-3 w-16">#</th>
                  <th className="px-4 py-3">Title</th>
                  {hasMultipleDiscs && <th className="px-4 py-3 w-20">Disc</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {sortedSongs.map((song) => (
                  <tr key={song.id} className="hover:bg-gray-50">
                    <td className="px-4 py-2.5 text-gray-400">{song.trackNumber}</td>
                    <td className="px-4 py-2.5 text-gray-800">{song.title}</td>
                    {hasMultipleDiscs && <td className="px-4 py-2.5 text-gray-400">{song.discNumber ?? 1}</td>}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
