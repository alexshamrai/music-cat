import { useState } from 'react';
import { useAlbums } from '../hooks/useAlbums';
import AlbumCard from '../components/AlbumCard';
import FilterBar from '../components/FilterBar';
import type { AlbumFilterParams } from '../types';

export default function AlbumListPage() {
  const [filters, setFilters] = useState<AlbumFilterParams>({});
  const { data: albums, isLoading, isError } = useAlbums(filters);

  return (
    <div className="p-4 sm:p-6 lg:p-8">
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Albums</h2>

      <FilterBar filters={filters} onChange={setFilters} />

      {isLoading && <div className="text-gray-500">Loading albums...</div>}
      {isError && <div className="text-red-500">Failed to load albums.</div>}

      {albums && (
        <>
          <p className="text-sm text-gray-500 mb-4">{albums.length} albums</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {albums.map((a) => <AlbumCard key={a.id} album={a} />)}
          </div>
        </>
      )}
    </div>
  );
}
