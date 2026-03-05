import { Link } from 'react-router';
import { Music } from 'lucide-react';
import StarRating from './StarRating';
import FavoriteToggle from './FavoriteToggle';
import TagBadge from './TagBadge';
import { useToggleAlbumFavorite } from '../hooks/useAlbums';
import type { AlbumSummary } from '../types';

export default function AlbumCard({ album }: { album: AlbumSummary }) {
  const toggleFav = useToggleAlbumFavorite();

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4 flex flex-col gap-2">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <Link to={`/albums/${album.id}`} className="text-sm font-medium text-gray-900 hover:text-blue-600 line-clamp-1">
            {album.title}
          </Link>
          <p className="text-xs text-gray-500 line-clamp-1">{album.artistName}</p>
        </div>
        <FavoriteToggle isFavorite={album.isFavorite} onToggle={() => toggleFav.mutate(album.id)} />
      </div>

      <div className="flex items-center gap-3 text-xs text-gray-400">
        {album.year && <span>{album.year}</span>}
        <span className="flex items-center gap-1"><Music size={12} />{album.songCount}</span>
      </div>

      <StarRating grade={album.grade ?? null} readonly />

      {album.tags.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {album.tags.map((t) => <TagBadge key={t} tag={t} />)}
        </div>
      )}
    </div>
  );
}
