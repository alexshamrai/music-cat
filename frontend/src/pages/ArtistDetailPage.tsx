import { useState } from 'react';
import { useParams, Link } from 'react-router';
import { ArrowLeft } from 'lucide-react';
import { useArtist, useToggleArtistFavorite, useSetArtistTags } from '../hooks/useArtists';
import { useAlbums } from '../hooks/useAlbums';
import FavoriteToggle from '../components/FavoriteToggle';
import TagBadge from '../components/TagBadge';
import AlbumCard from '../components/AlbumCard';

export default function ArtistDetailPage() {
  const { id } = useParams<{ id: string }>();
  const artistId = Number(id);
  const { data: artist, isLoading, isError } = useArtist(artistId);
  const { data: albums } = useAlbums({ artistId });
  const toggleFav = useToggleArtistFavorite();
  const setTags = useSetArtistTags();
  const [tagInput, setTagInput] = useState('');

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

  if (isLoading) {
    return (
      <div className="p-8">
        <div className="text-gray-500">Loading artist...</div>
      </div>
    );
  }

  if (isError || !artist) {
    return (
      <div className="p-8">
        <div className="text-red-500">Artist not found.</div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <Link to="/artists" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4">
        <ArrowLeft size={14} />
        Back to artists
      </Link>

      <div className="bg-white rounded-lg border border-gray-200 p-6 mb-6">
        <div className="flex items-start justify-between">
          <div>
            <h2 className="text-2xl font-bold text-gray-900">{artist.name}</h2>
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
      </div>

      <h3 className="text-lg font-semibold text-gray-900 mb-4">Albums</h3>
      {albums && albums.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {albums.map((a) => <AlbumCard key={a.id} album={a} />)}
        </div>
      ) : (
        <p className="text-sm text-gray-400">No albums found.</p>
      )}
    </div>
  );
}
