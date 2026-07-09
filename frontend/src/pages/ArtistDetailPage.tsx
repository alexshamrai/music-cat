import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router';
import { ArrowLeft, Pencil, Plus, Trash2 } from 'lucide-react';
import {
  useArtist,
  useToggleArtistFavorite,
  useSetArtistTags,
  useUpdateArtist,
  useDeleteArtist,
} from '../hooks/useArtists';
import { useAlbums, useCreateAlbum } from '../hooks/useAlbums';
import FavoriteToggle from '../components/FavoriteToggle';
import TagBadge from '../components/TagBadge';
import AlbumCard from '../components/AlbumCard';
import ConfirmDialog from '../components/ConfirmDialog';
import { GENRES } from '../constants';
import type { Artist } from '../types';

type ArtistDraft = { name: string; genre: string; subgenre: string };
type NewAlbum = { title: string; year: string };

const yearIsValid = (y: string) => y.trim() === '' || /^\d{1,4}$/.test(y.trim());

export default function ArtistDetailPage() {
  const { id } = useParams<{ id: string }>();
  const artistId = Number(id);
  const navigate = useNavigate();
  const { data: artist, isLoading, isError } = useArtist(artistId);
  const { data: albums } = useAlbums({ artistId });
  const toggleFav = useToggleArtistFavorite();
  const setTags = useSetArtistTags();
  const updateArtist = useUpdateArtist();
  const deleteArtist = useDeleteArtist();
  const createAlbum = useCreateAlbum();

  const [tagInput, setTagInput] = useState('');
  const [draft, setDraft] = useState<ArtistDraft | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [newAlbum, setNewAlbum] = useState<NewAlbum | null>(null);

  // Reset transient edit/create UI when navigating to a different artist.
  useEffect(() => {
    setDraft(null);
    setConfirmDelete(false);
    setNewAlbum(null);
  }, [artistId]);

  const addTag = () => {
    const t = tagInput.trim();
    if (!t || !artist) return;
    if (!artist.tags.includes(t)) {
      setTags.mutate({ id: artistId, tags: [...artist.tags, t] });
    }
    setTagInput('');
  };

  const removeTag = (tag: string) => {
    if (!artist) return;
    setTags.mutate({ id: artistId, tags: artist.tags.filter((t) => t !== tag) });
  };

  const startEdit = (a: Artist) => setDraft({ name: a.name, genre: a.genre, subgenre: a.subgenre ?? '' });

  const canSaveEdit = !!draft && draft.name.trim() !== '';

  const saveEdit = () => {
    if (!draft || !canSaveEdit) return;
    updateArtist.mutate(
      {
        id: artistId,
        dto: { name: draft.name.trim(), genre: draft.genre, subgenre: draft.subgenre.trim() },
      },
      { onSuccess: () => setDraft(null) },
    );
  };

  const doDeleteArtist = () =>
    deleteArtist.mutate(artistId, { onSuccess: () => navigate('/artists') });

  const canCreateAlbum = !!newAlbum && newAlbum.title.trim() !== '' && yearIsValid(newAlbum.year);

  const createNewAlbum = () => {
    if (!newAlbum || !canCreateAlbum) return;
    createAlbum.mutate(
      {
        title: newAlbum.title.trim(),
        year: newAlbum.year.trim() === '' ? undefined : Number(newAlbum.year),
        artistId,
      },
      { onSuccess: (created) => navigate(`/albums/${created.id}`) },
    );
  };

  if (isLoading) {
    return (
      <div className="p-4 sm:p-6 lg:p-8">
        <div className="text-gray-500">Loading artist...</div>
      </div>
    );
  }

  if (isError || !artist) {
    return (
      <div className="p-4 sm:p-6 lg:p-8">
        <div className="text-red-500">Artist not found.</div>
      </div>
    );
  }

  const isEditing = draft !== null;

  return (
    <div className="p-4 sm:p-6 lg:p-8">
      <Link to="/artists" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4">
        <ArrowLeft size={14} />
        Back to artists
      </Link>

      <div className="bg-white rounded-lg border border-gray-200 p-6 mb-6">
        {isEditing ? (
          <div className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Artist name</label>
              <input
                type="text"
                aria-label="Artist name"
                value={draft.name}
                onChange={(e) => setDraft((d) => (d ? { ...d, name: e.target.value } : d))}
                className="w-full text-lg font-semibold text-gray-900 border border-gray-300 rounded px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div className="flex flex-wrap gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">Genre</label>
                <select
                  aria-label="Artist genre"
                  value={draft.genre}
                  onChange={(e) => setDraft((d) => (d ? { ...d, genre: e.target.value } : d))}
                  className="text-sm border border-gray-300 rounded px-3 py-1.5 bg-white text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {GENRES.map((g) => (
                    <option key={g} value={g}>
                      {g}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">Subgenre</label>
                <input
                  type="text"
                  aria-label="Artist subgenre"
                  value={draft.subgenre}
                  onChange={(e) => setDraft((d) => (d ? { ...d, subgenre: e.target.value } : d))}
                  placeholder="—"
                  className="text-sm text-gray-900 border border-gray-300 rounded px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>
            <div className="flex items-center justify-between pt-1">
              <button
                type="button"
                onClick={() => setConfirmDelete(true)}
                className="inline-flex items-center gap-1 text-sm text-red-600 hover:text-red-700 cursor-pointer"
              >
                <Trash2 size={14} />
                Delete artist
              </button>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setDraft(null)}
                  disabled={updateArtist.isPending}
                  className="text-sm px-3 py-1.5 rounded border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={saveEdit}
                  disabled={!canSaveEdit || updateArtist.isPending}
                  className="text-sm px-3 py-1.5 rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 cursor-pointer"
                >
                  {updateArtist.isPending ? 'Saving…' : 'Save'}
                </button>
              </div>
            </div>
          </div>
        ) : (
          <>
            <div className="flex items-start justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-2xl font-bold text-gray-900">{artist.name}</h2>
                  <button
                    type="button"
                    onClick={() => startEdit(artist)}
                    aria-label="Edit artist"
                    className="text-gray-400 hover:text-gray-700 cursor-pointer"
                  >
                    <Pencil size={16} />
                  </button>
                </div>
                <div className="flex items-center gap-2 mt-1">
                  <span className="text-sm text-gray-500">{artist.genre}</span>
                  {artist.subgenre && <span className="text-sm text-gray-400">/ {artist.subgenre}</span>}
                </div>
                <p className="text-sm text-gray-400 mt-1">{artist.albumCount} albums</p>
              </div>
              <FavoriteToggle isFavorite={artist.isFavorite} onToggle={() => toggleFav.mutate(artistId)} />
            </div>

            <div className="mt-4">
              <p className="text-xs font-medium text-gray-500 mb-2">Tags</p>
              <div className="flex flex-wrap items-center gap-1.5">
                {artist.tags.map((t) => (
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
        <h3 className="text-lg font-semibold text-gray-900">Albums</h3>
        {!newAlbum && (
          <button
            type="button"
            onClick={() => setNewAlbum({ title: '', year: '' })}
            className="inline-flex items-center gap-1 text-sm px-3 py-1.5 bg-gray-900 text-white rounded-md hover:bg-gray-800 cursor-pointer"
          >
            <Plus size={14} />
            Add album
          </button>
        )}
      </div>

      {newAlbum && (
        <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex flex-wrap items-end gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">Title</label>
            <input
              type="text"
              aria-label="New album title"
              value={newAlbum.title}
              onChange={(e) => setNewAlbum((a) => (a ? { ...a, title: e.target.value } : a))}
              placeholder="Album title"
              className="text-sm border border-gray-300 rounded px-3 py-1.5 w-56 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">Year</label>
            <input
              type="text"
              inputMode="numeric"
              aria-label="New album year"
              value={newAlbum.year}
              onChange={(e) => setNewAlbum((a) => (a ? { ...a, year: e.target.value } : a))}
              placeholder="—"
              className={`text-sm border rounded px-3 py-1.5 w-24 focus:outline-none focus:ring-2 ${
                yearIsValid(newAlbum.year) ? 'border-gray-300 focus:ring-blue-500' : 'border-red-400 focus:ring-red-500'
              }`}
            />
          </div>
          <button
            type="button"
            onClick={createNewAlbum}
            disabled={!canCreateAlbum || createAlbum.isPending}
            className="text-sm px-3 py-1.5 rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 cursor-pointer"
          >
            {createAlbum.isPending ? 'Creating…' : 'Create'}
          </button>
          <button
            type="button"
            onClick={() => setNewAlbum(null)}
            disabled={createAlbum.isPending}
            className="text-sm px-3 py-1.5 rounded border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50 cursor-pointer"
          >
            Cancel
          </button>
        </div>
      )}

      {albums && albums.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {albums.map((a) => (
            <AlbumCard key={a.id} album={a} />
          ))}
        </div>
      ) : (
        <p className="text-sm text-gray-400">No albums found.</p>
      )}

      {confirmDelete && (
        <ConfirmDialog
          title="Delete artist?"
          message={
            <>
              Delete <span className="font-medium">{artist.name}</span>? This also permanently deletes its{' '}
              {artist.albumCount} album{artist.albumCount === 1 ? '' : 's'} and all their songs. This can't be undone.
            </>
          }
          confirmLabel="Delete artist"
          busy={deleteArtist.isPending}
          onConfirm={doDeleteArtist}
          onCancel={() => setConfirmDelete(false)}
        />
      )}
    </div>
  );
}
