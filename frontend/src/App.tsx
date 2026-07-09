import { BrowserRouter, Routes, Route } from 'react-router';
import { QueryClient, QueryClientProvider, MutationCache } from '@tanstack/react-query';
import axios from 'axios';
import Layout from './components/Layout';
import Toaster from './components/Toaster';
import * as toastStore from './toastStore';
import DashboardPage from './pages/DashboardPage';
import BrowsePage from './pages/BrowsePage';
import ArtistListPage from './pages/ArtistListPage';
import ArtistDetailPage from './pages/ArtistDetailPage';
import AlbumListPage from './pages/AlbumListPage';
import AlbumDetailPage from './pages/AlbumDetailPage';
import RandomPickPage from './pages/RandomPickPage';
import FavoritesPage from './pages/FavoritesPage';
import TagsPage from './pages/TagsPage';

function errorReason(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; error?: string } | string | undefined;
    if (typeof data === 'string') return data.trim() || error.message;
    if (data?.message) return data.message;
    if (data?.error) return data.error;
    if (error.response?.statusText) return error.response.statusText;
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return 'Unknown error';
}

// A single MutationCache surfaces every mutation as a toast (loading → saved/error),
// so the wait for the synchronous Google Sheets push is always visible. The per-hook
// onSuccess invalidations keep firing independently.
const queryClient = new QueryClient({
  mutationCache: new MutationCache({
    onMutate: (_variables, mutation) => {
      toastStore.start(mutation.mutationId);
    },
    onSuccess: (_data, _variables, _onMutateResult, mutation) => {
      toastStore.succeed(mutation.mutationId);
    },
    onError: (error, _variables, _onMutateResult, mutation) => {
      toastStore.fail(mutation.mutationId, `Couldn't save — ${errorReason(error)}`);
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<DashboardPage />} />
            <Route path="browse" element={<BrowsePage />} />
            <Route path="artists" element={<ArtistListPage />} />
            <Route path="artists/:id" element={<ArtistDetailPage />} />
            <Route path="albums" element={<AlbumListPage />} />
            <Route path="albums/:id" element={<AlbumDetailPage />} />
            <Route path="random" element={<RandomPickPage />} />
            <Route path="favorites" element={<FavoritesPage />} />
            <Route path="tags" element={<TagsPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
      <Toaster />
    </QueryClientProvider>
  );
}
