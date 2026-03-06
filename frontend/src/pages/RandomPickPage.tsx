import { useState, useCallback } from 'react';
import { Link } from 'react-router';
import { Shuffle, Loader2, Music } from 'lucide-react';
import { fetchRandomAlbum } from '../api/client';
import { useSetAlbumGrade, useToggleAlbumFavorite, useSetAlbumTags } from '../hooks/useAlbums';
import { useBrowseGenres } from '../hooks/useBrowse';
import StarRating from '../components/StarRating';
import FavoriteToggle from '../components/FavoriteToggle';
import TagBadge from '../components/TagBadge';
import type { Album, AlbumFilterParams } from '../types';

export default function RandomPickPage() {
  const [filters, setFilters] = useState<AlbumFilterParams>({});
  const [album, setAlbum] = useState<Album | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tagInput, setTagInput] = useState('');

  const { data: genres } = useBrowseGenres();
  const setGrade = useSetAlbumGrade();
  const toggleFav = useToggleAlbumFavorite();
  const setTags = useSetAlbumTags();

  const roll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchRandomAlbum(filters);
      setAlbum(result);
    } catch (err: any) {
      if (err?.response?.status === 404) {
        setError('No albums match your filters. Try broadening your search.');
        setAlbum(null);
      } else {
        setError('Something went wrong. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  }, [filters]);

  const updateFilter = (patch: Partial<AlbumFilterParams>) => setFilters((f) => ({ ...f, ...patch }));

  const addTag = () => {
    const t = tagInput.trim();
    if (!t || !album) return;
    if (!album.tags.includes(t)) {
      setTags.mutate({ id: album.id, tags: [...album.tags, t] }, {
        onSuccess: () => setAlbum((a) => a ? { ...a, tags: [...a.tags, t] } : a),
      });
    }
    setTagInput('');
  };

  const removeTag = (tag: string) => {
    if (!album) return;
    setTags.mutate({ id: album.id, tags: album.tags.filter((t) => t !== tag) }, {
      onSuccess: () => setAlbum((a) => a ? { ...a, tags: a.tags.filter((t) => t !== tag) } : a),
    });
  };

  const hasFilters = filters.genre || filters.minGrade || filters.favorite || (filters.tag && filters.tag.length > 0);

  return (
    <div className="p-8 max-w-4xl mx-auto">
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Random Pick</h2>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3 mb-8">
        <select
          value={filters.genre ?? ''}
          onChange={(e) => updateFilter({ genre: e.target.value || undefined })}
          className="text-sm border border-gray-300 rounded-md px-3 py-1.5 bg-white text-gray-700"
        >
          <option value="">All genres</option>
          {genres?.map((g) => <option key={g.genre} value={g.genre}>{g.genre}</option>)}
        </select>

        <select
          value={filters.minGrade ?? ''}
          onChange={(e) => updateFilter({ minGrade: e.target.value ? Number(e.target.value) : undefined })}
          className="text-sm border border-gray-300 rounded-md px-3 py-1.5 bg-white text-gray-700"
        >
          <option value="">Any grade</option>
          {[1, 2, 3, 4, 5].map((g) => <option key={g} value={g}>{g}+ stars</option>)}
        </select>

        <label className="flex items-center gap-1.5 text-sm text-gray-600">
          <input
            type="checkbox"
            checked={filters.favorite ?? false}
            onChange={(e) => updateFilter({ favorite: e.target.checked || undefined })}
            className="rounded"
          />
          Favorites only
        </label>

        {hasFilters && (
          <button
            type="button"
            onClick={() => setFilters({})}
            className="text-sm text-gray-500 hover:text-gray-700 cursor-pointer"
          >
            Clear filters
          </button>
        )}
      </div>

      {/* Surprise Me Button */}
      <div className="text-center mb-8">
        <button
          type="button"
          onClick={roll}
          disabled={loading}
          className="inline-flex items-center gap-2 px-8 py-4 bg-gray-900 text-white rounded-xl text-lg font-semibold hover:bg-gray-800 transition-colors cursor-pointer disabled:opacity-50"
        >
          {loading ? (
            <Loader2 size={22} className="animate-spin" />
          ) : (
            <Shuffle size={22} />
          )}
          {album ? 'Roll Again' : 'Surprise Me'}
        </button>
      </div>

      {/* Error */}
      {error && (
        <div className="text-center text-gray-500 py-8">{error}</div>
      )}

      {/* Result */}
      {album && !loading && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <div className="p-6">
            <div className="flex items-start justify-between mb-1">
              <div>
                <Link to={`/albums/${album.id}`} className="text-xl font-bold text-gray-900 hover:text-blue-600">
                  {album.title}
                </Link>
                <div className="flex items-center gap-2 mt-1">
                  <Link to={`/artists/${album.artist.id}`} className="text-sm text-blue-600 hover:text-blue-800">
                    {album.artist.name}
                  </Link>
                  <span className="text-sm text-gray-400">{album.artist.genre}</span>
                  {album.year && <span className="text-sm text-gray-400">&middot; {album.year}</span>}
                </div>
              </div>
              <FavoriteToggle
                isFavorite={album.isFavorite}
                onToggle={() => toggleFav.mutate(album.id, {
                  onSuccess: () => setAlbum((a) => a ? { ...a, isFavorite: !a.isFavorite } : a),
                })}
              />
            </div>

            <div className="mt-4">
              <p className="text-xs font-medium text-gray-500 mb-1">Rating</p>
              <StarRating
                grade={album.grade ?? null}
                onChange={(grade) => setGrade.mutate({ id: album.id, grade }, {
                  onSuccess: () => setAlbum((a) => a ? { ...a, grade } : a),
                })}
              />
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
          </div>

          {/* Song list */}
          <div className="border-t border-gray-100">
            <div className="px-6 py-3 flex items-center gap-2 text-sm font-medium text-gray-700">
              <Music size={14} />
              Songs ({album.songs.length})
            </div>
            <div className="divide-y divide-gray-50">
              {album.songs
                .sort((a, b) => (a.discNumber ?? 1) - (b.discNumber ?? 1) || a.trackNumber - b.trackNumber)
                .map((song) => (
                  <div key={song.id} className="px-6 py-2 flex items-center gap-4 text-sm">
                    <span className="text-gray-400 w-6 text-right">{song.trackNumber}</span>
                    <span className="text-gray-800">{song.title}</span>
                  </div>
                ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
