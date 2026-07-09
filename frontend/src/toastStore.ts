// A tiny dependency-free store for transient "save" toasts, wired to TanStack
// Query's global MutationCache (see App.tsx). Compatible with useSyncExternalStore:
// getSnapshot returns a stable array reference that only changes on mutation.
export type ToastStatus = 'loading' | 'success' | 'error';

export interface Toast {
  id: number;
  status: ToastStatus;
  message: string;
}

const SUCCESS_TTL = 2500;
const ERROR_TTL = 6000;

let toasts: Toast[] = [];
const listeners = new Set<() => void>();
const timers = new Map<number, ReturnType<typeof setTimeout>>();

function notify() {
  for (const listener of listeners) listener();
}

function clearTimer(id: number) {
  const timer = timers.get(id);
  if (timer !== undefined) {
    clearTimeout(timer);
    timers.delete(id);
  }
}

function scheduleRemoval(id: number, ttl: number) {
  clearTimer(id);
  timers.set(id, setTimeout(() => dismiss(id), ttl));
}

function upsert(id: number, patch: Partial<Omit<Toast, 'id'>>) {
  const idx = toasts.findIndex((t) => t.id === id);
  if (idx >= 0) {
    toasts = toasts.map((t) => (t.id === id ? { ...t, ...patch } : t));
  } else {
    toasts = [...toasts, { id, status: 'loading', message: '', ...patch }];
  }
  notify();
}

export function start(id: number, message = 'Saving to Google Sheets…') {
  clearTimer(id); // in case this id is reused after a prior success/error
  upsert(id, { status: 'loading', message });
}

export function succeed(id: number, message = 'Saved') {
  upsert(id, { status: 'success', message });
  scheduleRemoval(id, SUCCESS_TTL);
}

export function fail(id: number, message = "Couldn't save") {
  upsert(id, { status: 'error', message });
  scheduleRemoval(id, ERROR_TTL);
}

export function dismiss(id: number) {
  clearTimer(id);
  const next = toasts.filter((t) => t.id !== id);
  if (next.length !== toasts.length) {
    toasts = next;
    notify();
  }
}

export function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function getSnapshot() {
  return toasts;
}
