import { BrowserRouter, Routes, Route } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Layout from './components/Layout';
import DashboardPage from './pages/DashboardPage';
import BrowsePage from './pages/BrowsePage';
import ArtistListPage from './pages/ArtistListPage';
import ArtistDetailPage from './pages/ArtistDetailPage';
import AlbumListPage from './pages/AlbumListPage';
import AlbumDetailPage from './pages/AlbumDetailPage';
import RandomPickPage from './pages/RandomPickPage';
import FavoritesPage from './pages/FavoritesPage';
import TagsPage from './pages/TagsPage';

const queryClient = new QueryClient({
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
    </QueryClientProvider>
  );
}
