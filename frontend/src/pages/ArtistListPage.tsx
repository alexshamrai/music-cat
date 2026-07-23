import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Plus } from 'lucide-react';
import { useBrowseGenres } from '../hooks/useBrowse';
import { useArtists, useCreateArtist } from '../hooks/useArtists';
import { GENRES } from '../constants';
import ArtistCard from '../components/ArtistCard';

type NewArtist = { name: string; genre: string; subgenre: string };

export default function ArtistListPage() {
  const navigate = useNavigate();
  const [genre, setGenre] = useState<string>('');
  const [favorite, setFavorite] = useState(false);
  const [newArtist, setNewArtist] = useState<NewArtist | null>(null);
  const { data: genres } = useBrowseGenres();
  const { data: artists, isLoading, isError } = useArtists({
    genre: genre || undefined,
    favorite: favorite || undefined,
  });
  const createArtist = useCreateArtist();

  const canCreate = !!newArtist && newArtist.name.trim() !== '' && newArtist.genre !== '';

  const createNewArtist = () => {
    if (!newArtist || !canCreate) return;
    createArtist.mutate(
      {
        name: newArtist.name.trim(),
        genre: newArtist.genre,
        subgenre: newArtist.subgenre.trim() || undefined,
      },
      { onSuccess: (created) => navigate(`/artists/${created.id}`) },
    );
  };

  return (
    <div className="p-4 sm:p-6 lg:p-8">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">Artists</h2>
        {!newArtist && (
          <button
            type="button"
            onClick={() => setNewArtist({ name: '', genre: '', subgenre: '' })}
            className="inline-flex items-center gap-1 text-sm px-3 py-1.5 bg-gray-900 text-white rounded-md hover:bg-gray-800 cursor-pointer"
          >
            <Plus size={14} />
            Add artist
          </button>
        )}
      </div>

      {newArtist && (
        <div className="bg-white rounded-lg border border-gray-200 p-4 mb-6 flex flex-wrap items-end gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">Name</label>
            <input
              type="text"
              aria-label="New artist name"
              value={newArtist.name}
              onChange={(e) => setNewArtist((a) => (a ? { ...a, name: e.target.value } : a))}
              placeholder="Artist name"
              className="text-sm border border-gray-300 rounded px-3 py-1.5 w-56 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">Genre</label>
            <select
              aria-label="New artist genre"
              value={newArtist.genre}
              onChange={(e) => setNewArtist((a) => (a ? { ...a, genre: e.target.value } : a))}
              className="text-sm border border-gray-300 rounded px-3 py-1.5 bg-white text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="" disabled>
                Select a genre…
              </option>
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
              aria-label="New artist subgenre"
              value={newArtist.subgenre}
              onChange={(e) => setNewArtist((a) => (a ? { ...a, subgenre: e.target.value } : a))}
              placeholder="—"
              className="text-sm border border-gray-300 rounded px-3 py-1.5 w-40 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <button
            type="button"
            onClick={createNewArtist}
            disabled={!canCreate || createArtist.isPending}
            className="text-sm px-3 py-1.5 rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 cursor-pointer"
          >
            {createArtist.isPending ? 'Creating…' : 'Create'}
          </button>
          <button
            type="button"
            onClick={() => setNewArtist(null)}
            disabled={createArtist.isPending}
            className="text-sm px-3 py-1.5 rounded border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50 cursor-pointer"
          >
            Cancel
          </button>
        </div>
      )}

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
