import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from '../api/client';

export const useBrowseGenres = () =>
  useQuery({
    queryKey: ['genres'],
    queryFn: api.fetchGenres,
  });

export const useArtistsByGenre = (genre: string) =>
  useQuery({
    queryKey: ['genres', genre, 'artists'],
    queryFn: () => api.fetchArtistsByGenre(genre),
    enabled: !!genre,
  });

export const useAlbumsByArtist = (genre: string, artistId: number) =>
  useQuery({
    queryKey: ['genres', genre, 'artists', artistId, 'albums'],
    queryFn: () => api.fetchAlbumsByArtist(genre, artistId),
    enabled: !!genre && !!artistId,
  });

export const useBrowseTags = () =>
  useQuery({
    queryKey: ['tagStats'],
    queryFn: api.fetchTagStats,
  });

export const useBrowseStats = () =>
  useQuery({
    queryKey: ['stats'],
    queryFn: api.fetchStats,
  });

export const useFavorites = () =>
  useQuery({
    queryKey: ['favorites'],
    queryFn: api.fetchFavorites,
  });

export const useCreateTag = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => api.createTag(name),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tags'] });
      qc.invalidateQueries({ queryKey: ['tagStats'] });
    },
  });
};

export const useDeleteTag = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.deleteTag(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tags'] });
      qc.invalidateQueries({ queryKey: ['tagStats'] });
    },
  });
};

export const useTags = () =>
  useQuery({
    queryKey: ['tags'],
    queryFn: api.fetchTags,
  });
