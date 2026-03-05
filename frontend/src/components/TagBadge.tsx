import { X } from 'lucide-react';

interface TagBadgeProps {
  tag: string;
  onRemove?: () => void;
  onClick?: () => void;
}

export default function TagBadge({ tag, onRemove, onClick }: TagBadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-gray-100 text-xs text-gray-600 ${onClick ? 'cursor-pointer hover:bg-gray-200' : ''}`}
      onClick={onClick}
    >
      {tag}
      {onRemove && (
        <button
          type="button"
          onClick={(e) => { e.stopPropagation(); onRemove(); }}
          className="hover:text-gray-900 cursor-pointer"
        >
          <X size={12} />
        </button>
      )}
    </span>
  );
}
