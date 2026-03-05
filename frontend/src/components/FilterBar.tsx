import { useState } from 'react';
import { X } from 'lucide-react';
import { useBrowseGenres } from '../hooks/useBrowse';
import type { AlbumFilterParams } from '../types';

interface FilterBarProps {
  filters: AlbumFilterParams;
  onChange: (filters: AlbumFilterParams) => void;
}

export default function FilterBar({ filters, onChange }: FilterBarProps) {
  const { data: genres } = useBrowseGenres();
  const [tagInput, setTagInput] = useState('');

  const update = (patch: Partial<AlbumFilterParams>) => onChange({ ...filters, ...patch });

  const addTag = () => {
    const t = tagInput.trim();
    if (!t) return;
    const current = filters.tag ?? [];
    if (!current.includes(t)) {
      update({ tag: [...current, t] });
    }
    setTagInput('');
  };

  const removeTag = (tag: string) => {
    update({ tag: (filters.tag ?? []).filter((t) => t !== tag) });
  };

  const clear = () => onChange({});

  const hasFilters = filters.genre || filters.minGrade || filters.favorite || (filters.tag && filters.tag.length > 0) || filters.unrated;

  return (
    <div className="flex flex-wrap items-center gap-3 mb-6">
      <select
        value={filters.genre ?? ''}
        onChange={(e) => update({ genre: e.target.value || undefined })}
        className="text-sm border border-gray-300 rounded-md px-3 py-1.5 bg-white text-gray-700"
      >
        <option value="">All genres</option>
        {genres?.map((g) => <option key={g.genre} value={g.genre}>{g.genre}</option>)}
      </select>

      <select
        value={filters.minGrade ?? ''}
        onChange={(e) => update({ minGrade: e.target.value ? Number(e.target.value) : undefined })}
        className="text-sm border border-gray-300 rounded-md px-3 py-1.5 bg-white text-gray-700"
      >
        <option value="">Any grade</option>
        {[1, 2, 3, 4, 5].map((g) => <option key={g} value={g}>{g}+ stars</option>)}
      </select>

      <label className="flex items-center gap-1.5 text-sm text-gray-600">
        <input
          type="checkbox"
          checked={filters.favorite ?? false}
          onChange={(e) => update({ favorite: e.target.checked || undefined })}
          className="rounded"
        />
        Favorites
      </label>

      <label className="flex items-center gap-1.5 text-sm text-gray-600">
        <input
          type="checkbox"
          checked={filters.unrated ?? false}
          onChange={(e) => update({ unrated: e.target.checked || undefined })}
          className="rounded"
        />
        Unrated
      </label>

      <input
        type="text"
        value={tagInput}
        onChange={(e) => setTagInput(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && addTag()}
        placeholder="Add tag filter..."
        className="text-sm border border-gray-300 rounded-md px-3 py-1.5 w-40"
      />

      {(filters.tag ?? []).map((t) => (
        <span key={t} className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-blue-100 text-xs text-blue-700">
          {t}
          <button type="button" onClick={() => removeTag(t)} className="hover:text-blue-900 cursor-pointer"><X size={12} /></button>
        </span>
      ))}

      {hasFilters && (
        <button type="button" onClick={clear} className="text-sm text-gray-500 hover:text-gray-700 cursor-pointer">
          Clear all
        </button>
      )}
    </div>
  );
}
