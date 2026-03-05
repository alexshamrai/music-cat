import { useState } from 'react';
import { Link } from 'react-router';
import { ChevronDown, ChevronRight, Mic2, Disc3 } from 'lucide-react';
import { useBrowseGenres, useArtistsByGenre } from '../hooks/useBrowse';

function GenreArtists({ genre }: { genre: string }) {
  const { data: artists, isLoading } = useArtistsByGenre(genre);

  if (isLoading) return <div className="p-4 text-sm text-gray-500">Loading artists...</div>;
  if (!artists?.length) return <div className="p-4 text-sm text-gray-400">No artists found.</div>;

  return (
    <div className="border-t border-gray-100 divide-y divide-gray-50">
      {artists.map((artist) => (
        <Link
          key={artist.id}
          to={`/artists/${artist.id}`}
          className="flex items-center justify-between px-5 py-3 hover:bg-gray-50 transition-colors"
        >
          <div className="flex items-center gap-3">
            <Mic2 size={14} className="text-gray-400" />
            <span className="text-sm text-gray-800">{artist.name}</span>
          </div>
          <span className="flex items-center gap-1 text-xs text-gray-400">
            <Disc3 size={12} />
            {artist.albumCount}
          </span>
        </Link>
      ))}
    </div>
  );
}

export default function BrowsePage() {
  const { data: genres, isLoading, isError } = useBrowseGenres();
  const [expanded, setExpanded] = useState<string | null>(null);

  if (isLoading) {
    return (
      <div className="p-8">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Browse</h2>
        <div className="text-gray-500">Loading genres...</div>
      </div>
    );
  }

  if (isError || !genres) {
    return (
      <div className="p-8">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Browse</h2>
        <div className="text-red-500">Failed to load genres.</div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Browse</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {genres.map((g) => (
          <div key={g.genre} className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <button
              type="button"
              onClick={() => setExpanded(expanded === g.genre ? null : g.genre)}
              className="w-full flex items-center justify-between p-5 text-left cursor-pointer hover:bg-gray-50 transition-colors"
            >
              <div>
                <p className="font-medium text-gray-900">{g.genre}</p>
                <p className="text-xs text-gray-500 mt-1">
                  {g.artistCount} artists &middot; {g.albumCount} albums
                </p>
              </div>
              {expanded === g.genre ? (
                <ChevronDown size={18} className="text-gray-400" />
              ) : (
                <ChevronRight size={18} className="text-gray-400" />
              )}
            </button>
            {expanded === g.genre && <GenreArtists genre={g.genre} />}
          </div>
        ))}
      </div>
    </div>
  );
}
