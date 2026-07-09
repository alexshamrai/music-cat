import { useSyncExternalStore } from 'react';
import { Loader2, CheckCircle2, AlertCircle, X } from 'lucide-react';
import { subscribe, getSnapshot, dismiss } from '../toastStore';

// Bottom-right stack that surfaces the in-flight/finished state of every mutation.
// Sits above the drawer scrim (z-40/50) and the TagsPage modal (z-50) at z-[60].
export default function Toaster() {
  const toasts = useSyncExternalStore(subscribe, getSnapshot, getSnapshot);

  if (toasts.length === 0) return null;

  return (
    <div
      className="fixed bottom-4 right-4 z-[60] flex flex-col gap-2 w-[min(20rem,calc(100vw-2rem))]"
      role="status"
      aria-live="polite"
    >
      {toasts.map((t) => (
        <div
          key={t.id}
          className={`flex items-center gap-2.5 rounded-lg border bg-white px-3.5 py-2.5 text-sm shadow-lg ${
            t.status === 'error' ? 'border-red-200' : 'border-gray-200'
          }`}
        >
          {t.status === 'loading' && <Loader2 size={16} className="shrink-0 animate-spin text-gray-500" />}
          {t.status === 'success' && <CheckCircle2 size={16} className="shrink-0 text-green-600" />}
          {t.status === 'error' && <AlertCircle size={16} className="shrink-0 text-red-600" />}
          <span className={`flex-1 ${t.status === 'error' ? 'text-red-700' : 'text-gray-700'}`}>{t.message}</span>
          {/* Dismissable in every state, including 'loading' — an offline mutation is paused by
              TanStack Query and its loading toast would otherwise never resolve on its own. */}
          <button
            type="button"
            onClick={() => dismiss(t.id)}
            aria-label="Dismiss"
            className="shrink-0 text-gray-400 hover:text-gray-600 cursor-pointer"
          >
            <X size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}
