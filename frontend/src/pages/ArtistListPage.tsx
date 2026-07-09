import { useState } from 'react';
import { useBrowseGenres } from '../hooks/useBrowse';
import { useArtists } from '../hooks/useArtists';
import ArtistCard from '../components/ArtistCard';

export default function ArtistListPage() {
  const [genre, setGenre] = useState<string>('');
  const [favorite, setFavorite] = useState(false);
  const { data: genres } = useBrowseGenres();
  const { data: artists, isLoading, isError } = useArtists({
    genre: genre || undefined,
    favorite: favorite || undefined,
  });

  return (
    <div className="p-4 sm:p-6 lg:p-8">
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Artists</h2>

      <div className="flex flex-wrap items-center gap-3 mb-6">
        <select
          value={genre}
          onChange={(e) => setGenre(e.target.value)}
          className="text-sm border border-gray-300 rounded-md px-3 py-1.5 bg-white text-gray-700"
        >
          <option value="">All genres</option>
          {genres?.map((g) => <option key={g.genre} value={g.genre}>{g.genre}</option>)}
        </select>

        <label className="flex items-center gap-1.5 text-sm text-gray-600">
          <input
            type="checkbox"
            checked={favorite}
            onChange={(e) => setFavorite(e.target.checked)}
            className="rounded"
          />
          Favorites only
        </label>

        {(genre || favorite) && (
          <button
            type="button"
            onClick={() => { setGenre(''); setFavorite(false); }}
            className="text-sm text-gray-500 hover:text-gray-700 cursor-pointer"
          >
            Clear filters
          </button>
        )}
      </div>

      {isLoading && <div className="text-gray-500">Loading artists...</div>}
      {isError && <div className="text-red-500">Failed to load artists.</div>}

      {artists && (
        <>
          <p className="text-sm text-gray-500 mb-4">{artists.length} artists</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {artists.map((a) => <ArtistCard key={a.id} artist={a} />)}
          </div>
        </>
      )}
    </div>
  );
}
