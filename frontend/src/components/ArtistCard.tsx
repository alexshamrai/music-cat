import { Link } from 'react-router';
import { Disc3 } from 'lucide-react';
import FavoriteToggle from './FavoriteToggle';
import TagBadge from './TagBadge';
import { useToggleArtistFavorite } from '../hooks/useArtists';
import type { Artist } from '../types';

export default function ArtistCard({ artist }: { artist: Artist }) {
  const toggleFav = useToggleArtistFavorite();

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4 flex flex-col gap-2">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <Link to={`/artists/${artist.id}`} className="text-sm font-medium text-gray-900 hover:text-blue-600 line-clamp-1">
            {artist.name}
          </Link>
          <div className="flex items-center gap-2 mt-0.5">
            <span className="text-xs text-gray-500">{artist.genre}</span>
            {artist.subgenre && <span className="text-xs text-gray-400">/ {artist.subgenre}</span>}
          </div>
        </div>
        <FavoriteToggle isFavorite={artist.isFavorite} onToggle={() => toggleFav.mutate(artist.id)} />
      </div>

      <div className="flex items-center gap-1 text-xs text-gray-400">
        <Disc3 size={12} />
        <span>{artist.albumCount} albums</span>
      </div>

      {artist.tags.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {artist.tags.map((t) => <TagBadge key={t} tag={t} />)}
        </div>
      )}
    </div>
  );
}
