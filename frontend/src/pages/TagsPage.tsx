import { useState } from 'react';
import { ArrowUpDown, Plus, Trash2, X } from 'lucide-react';
import { useBrowseTags } from '../hooks/useBrowse';
import { useArtists } from '../hooks/useArtists';
import { useAlbums } from '../hooks/useAlbums';
import ArtistCard from '../components/ArtistCard';
import AlbumCard from '../components/AlbumCard';
import type { TagBrowse } from '../types';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createTag, deleteTag, fetchTags } from '../api/client';

function useCreateTag() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => createTag(name),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tags'] });
      qc.invalidateQueries({ queryKey: ['tagStats'] });
    },
  });
}

function useDeleteTag() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteTag(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tags'] });
      qc.invalidateQueries({ queryKey: ['tagStats'] });
    },
  });
}

type SortMode = 'name' | 'usage';

export default function TagsPage() {
  const { data: tagStats, isLoading, isError } = useBrowseTags();
  const [sortBy, setSortBy] = useState<SortMode>('usage');
  const [selectedTag, setSelectedTag] = useState<string | null>(null);
  const [newTagName, setNewTagName] = useState('');
  const [deleteConfirm, setDeleteConfirm] = useState<TagBrowse | null>(null);

  const createTagMut = useCreateTag();
  const deleteTagMut = useDeleteTag();

  const { data: taggedArtists } = useArtists(selectedTag ? { tag: selectedTag } : undefined);
  const { data: taggedAlbums } = useAlbums(selectedTag ? { tag: [selectedTag] } : undefined);

  const handleCreate = () => {
    const name = newTagName.trim();
    if (!name) return;
    createTagMut.mutate(name);
    setNewTagName('');
  };

  const handleDelete = (tag: TagBrowse) => {
    setDeleteConfirm(tag);
  };

  const confirmDelete = async () => {
    if (!deleteConfirm) return;
    const tags = await fetchTags();
    const found = tags.find((t) => t.name === deleteConfirm.tag);
    if (found) {
      deleteTagMut.mutate(found.id);
      if (selectedTag === deleteConfirm.tag) setSelectedTag(null);
    }
    setDeleteConfirm(null);
  };

  if (isLoading) {
    return (
      <div className="p-8">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Tags</h2>
        <div className="text-gray-500">Loading tags...</div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="p-8">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Tags</h2>
        <div className="text-red-500">Failed to load tags.</div>
      </div>
    );
  }

  const sorted = [...(tagStats ?? [])].sort((a, b) => {
    if (sortBy === 'name') return a.tag.localeCompare(b.tag);
    return (b.artistCount + b.albumCount) - (a.artistCount + a.albumCount);
  });

  return (
    <div className="p-8">
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Tags</h2>

      {/* Create + Sort */}
      <div className="flex flex-wrap items-center gap-3 mb-6">
        <input
          type="text"
          value={newTagName}
          onChange={(e) => setNewTagName(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
          placeholder="New tag name..."
          className="text-sm border border-gray-300 rounded-md px-3 py-1.5 w-48"
        />
        <button
          type="button"
          onClick={handleCreate}
          disabled={!newTagName.trim()}
          className="inline-flex items-center gap-1 text-sm px-3 py-1.5 bg-gray-900 text-white rounded-md hover:bg-gray-800 disabled:opacity-40 cursor-pointer"
        >
          <Plus size={14} />
          Create Tag
        </button>

        <div className="ml-auto">
          <button
            type="button"
            onClick={() => setSortBy(sortBy === 'name' ? 'usage' : 'name')}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 cursor-pointer"
          >
            <ArrowUpDown size={14} />
            Sort by {sortBy === 'name' ? 'usage' : 'name'}
          </button>
        </div>
      </div>

      {sorted.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <p className="text-lg">No tags yet.</p>
          <p className="text-sm mt-1">Create a tag above or add tags to artists and albums.</p>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2 mb-8">
          {sorted.map((t) => {
            const isSelected = selectedTag === t.tag;
            return (
              <button
                key={t.tag}
                type="button"
                onClick={() => setSelectedTag(isSelected ? null : t.tag)}
                className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-sm transition-colors cursor-pointer ${
                  isSelected
                    ? 'bg-gray-900 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {t.tag}
                <span className={`text-xs ${isSelected ? 'text-gray-300' : 'text-gray-400'}`}>
                  {t.artistCount}a &middot; {t.albumCount}al
                </span>
              </button>
            );
          })}
        </div>
      )}

      {/* Selected tag detail */}
      {selectedTag && (
        <div className="border-t border-gray-200 pt-6">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              <h3 className="text-lg font-semibold text-gray-900">Tag: {selectedTag}</h3>
              <button
                type="button"
                onClick={() => setSelectedTag(null)}
                className="text-gray-400 hover:text-gray-600 cursor-pointer"
              >
                <X size={16} />
              </button>
            </div>
            <button
              type="button"
              onClick={() => handleDelete(sorted.find((t) => t.tag === selectedTag)!)}
              className="inline-flex items-center gap-1 text-sm text-red-500 hover:text-red-700 cursor-pointer"
            >
              <Trash2 size={14} />
              Delete tag
            </button>
          </div>

          {taggedArtists && taggedArtists.length > 0 && (
            <>
              <h4 className="text-sm font-medium text-gray-600 mb-3">Artists ({taggedArtists.length})</h4>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 mb-6">
                {taggedArtists.map((a) => <ArtistCard key={a.id} artist={a} />)}
              </div>
            </>
          )}

          {taggedAlbums && taggedAlbums.length > 0 && (
            <>
              <h4 className="text-sm font-medium text-gray-600 mb-3">Albums ({taggedAlbums.length})</h4>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                {taggedAlbums.map((a) => <AlbumCard key={a.id} album={a} />)}
              </div>
            </>
          )}

          {taggedArtists?.length === 0 && taggedAlbums?.length === 0 && (
            <p className="text-sm text-gray-400">No artists or albums with this tag.</p>
          )}
        </div>
      )}

      {/* Delete confirmation */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-sm w-full mx-4 shadow-xl">
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Delete tag?</h3>
            <p className="text-sm text-gray-600 mb-4">
              Are you sure you want to delete "{deleteConfirm.tag}"? This will remove it from all artists and albums.
            </p>
            <div className="flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setDeleteConfirm(null)}
                className="text-sm px-3 py-1.5 text-gray-600 hover:text-gray-800 cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={confirmDelete}
                className="text-sm px-3 py-1.5 bg-red-600 text-white rounded-md hover:bg-red-700 cursor-pointer"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
