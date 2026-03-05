import { useState } from 'react';
import { useParams, Link } from 'react-router';
import { ArrowLeft } from 'lucide-react';
import { useAlbum, useSetAlbumGrade, useToggleAlbumFavorite, useSetAlbumTags } from '../hooks/useAlbums';
import StarRating from '../components/StarRating';
import FavoriteToggle from '../components/FavoriteToggle';
import TagBadge from '../components/TagBadge';

export default function AlbumDetailPage() {
  const { id } = useParams<{ id: string }>();
  const albumId = Number(id);
  const { data: album, isLoading, isError } = useAlbum(albumId);
  const setGrade = useSetAlbumGrade();
  const toggleFav = useToggleAlbumFavorite();
  const setTags = useSetAlbumTags();
  const [tagInput, setTagInput] = useState('');

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

  if (isLoading) {
    return (
      <div className="p-8">
        <div className="text-gray-500">Loading album...</div>
      </div>
    );
  }

  if (isError || !album) {
    return (
      <div className="p-8">
        <div className="text-red-500">Album not found.</div>
      </div>
    );
  }

  const hasMultipleDiscs = album.songs.some((s) => s.discNumber && s.discNumber > 1);

  return (
    <div className="p-8">
      <Link to="/albums" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4">
        <ArrowLeft size={14} />
        Back to albums
      </Link>

      <div className="bg-white rounded-lg border border-gray-200 p-6 mb-6">
        <div className="flex items-start justify-between">
          <div>
            <h2 className="text-2xl font-bold text-gray-900">{album.title}</h2>
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
      </div>

      <h3 className="text-lg font-semibold text-gray-900 mb-4">
        Songs <span className="text-sm font-normal text-gray-400">({album.songs.length})</span>
      </h3>
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100 text-left text-xs text-gray-500 uppercase tracking-wider">
              <th className="px-4 py-3 w-16">#</th>
              <th className="px-4 py-3">Title</th>
              {hasMultipleDiscs && <th className="px-4 py-3 w-20">Disc</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {album.songs
              .sort((a, b) => (a.discNumber ?? 1) - (b.discNumber ?? 1) || a.trackNumber - b.trackNumber)
              .map((song) => (
                <tr key={song.id} className="hover:bg-gray-50">
                  <td className="px-4 py-2.5 text-gray-400">{song.trackNumber}</td>
                  <td className="px-4 py-2.5 text-gray-800">{song.title}</td>
                  {hasMultipleDiscs && <td className="px-4 py-2.5 text-gray-400">{song.discNumber ?? 1}</td>}
                </tr>
              ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
