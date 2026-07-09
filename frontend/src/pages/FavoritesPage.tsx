import { Heart } from 'lucide-react';
import { useFavorites } from '../hooks/useBrowse';
import ArtistCard from '../components/ArtistCard';
import AlbumCard from '../components/AlbumCard';

export default function FavoritesPage() {
  const { data: favorites, isLoading, isError } = useFavorites();

  if (isLoading) {
    return (
      <div className="p-4 sm:p-6 lg:p-8">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Favorites</h2>
        <div className="text-gray-500">Loading favorites...</div>
      </div>
    );
  }

  if (isError || !favorites) {
    return (
      <div className="p-4 sm:p-6 lg:p-8">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Favorites</h2>
        <div className="text-red-500">Failed to load favorites.</div>
      </div>
    );
  }

  const hasAny = favorites.favoriteArtists.length > 0 || favorites.favoriteAlbums.length > 0;

  if (!hasAny) {
    return (
      <div className="p-4 sm:p-6 lg:p-8">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Favorites</h2>
        <div className="flex flex-col items-center justify-center py-16 text-gray-400">
          <Heart size={48} className="mb-4" />
          <p className="text-lg">No favorites yet.</p>
          <p className="text-sm mt-1">Browse your library and heart the ones you love.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="p-4 sm:p-6 lg:p-8">
      <h2 className="text-2xl font-bold text-gray-900 mb-2">Favorites</h2>
      <p className="text-sm text-gray-500 mb-6">
        {favorites.favoriteArtists.length} favorite artists, {favorites.favoriteAlbums.length} favorite albums
      </p>

      {favorites.favoriteArtists.length > 0 && (
        <>
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Favorite Artists</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 mb-8">
            {favorites.favoriteArtists.map((a) => <ArtistCard key={a.id} artist={a} />)}
          </div>
        </>
      )}

      {favorites.favoriteAlbums.length > 0 && (
        <>
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Favorite Albums</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {favorites.favoriteAlbums.map((a) => <AlbumCard key={a.id} album={a} />)}
          </div>
        </>
      )}
    </div>
  );
}
