import { Star } from 'lucide-react';

interface StarRatingProps {
  grade: number | null;
  onChange?: (grade: number) => void;
  readonly?: boolean;
}

export default function StarRating({ grade, onChange, readonly = false }: StarRatingProps) {
  if (grade === null && readonly) {
    return <span className="text-xs text-gray-400">Unrated</span>;
  }

  return (
    <span className="inline-flex gap-0.5">
      {[1, 2, 3, 4, 5].map((star) => (
        <button
          key={star}
          type="button"
          disabled={readonly}
          onClick={() => onChange?.(star)}
          className={`${readonly ? 'cursor-default' : 'cursor-pointer hover:scale-110'} transition-transform`}
        >
          <Star
            size={16}
            className={star <= (grade ?? 0) ? 'fill-yellow-400 text-yellow-400' : 'text-gray-300'}
          />
        </button>
      ))}
    </span>
  );
}
