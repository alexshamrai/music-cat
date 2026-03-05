import { Heart } from 'lucide-react';

interface FavoriteToggleProps {
  isFavorite: boolean;
  onToggle: () => void;
}

export default function FavoriteToggle({ isFavorite, onToggle }: FavoriteToggleProps) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className="cursor-pointer hover:scale-110 transition-transform"
      aria-label={isFavorite ? 'Remove from favorites' : 'Add to favorites'}
    >
      <Heart
        size={18}
        className={isFavorite ? 'fill-red-500 text-red-500' : 'text-gray-400 hover:text-red-400'}
      />
    </button>
  );
}
