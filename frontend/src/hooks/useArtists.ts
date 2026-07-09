import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from '../api/client';
import type { ArtistCreateDto, ArtistUpdateDto } from '../types';

export const useArtists = (filters?: { genre?: string; subgenre?: string; favorite?: boolean; tag?: string }) =>
  useQuery({
    queryKey: ['artists', filters],
    queryFn: () => api.fetchArtists(filters),
  });

export const useArtist = (id: number) =>
  useQuery({
    queryKey: ['artists', id],
    queryFn: () => api.fetchArtist(id),
    enabled: !!id,
  });

export const useCreateArtist = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (dto: ArtistCreateDto) => api.createArtist(dto),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['artists'] }),
  });
};

export const useUpdateArtist = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dto }: { id: number; dto: ArtistUpdateDto }) => api.updateArtist(id, dto),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: ['artists'] });
      qc.invalidateQueries({ queryKey: ['artists', id] });
    },
  });
};

export const useDeleteArtist = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.deleteArtist(id),
    // Invalidate artist LIST queries only — not the deleted artist's detail (['artists', <number>]),
    // which would otherwise refetch a now-404 resource while the detail page navigates away.
    // Also refresh albums (deleting an artist cascade-deletes its albums), stats and favorites.
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['artists'], predicate: (q) => typeof q.queryKey[1] !== 'number' });
      qc.invalidateQueries({ queryKey: ['albums'] });
      qc.invalidateQueries({ queryKey: ['stats'] });
      qc.invalidateQueries({ queryKey: ['favorites'] });
    },
  });
};

export const useToggleArtistFavorite = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.toggleArtistFavorite(id),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: ['artists'] });
      qc.invalidateQueries({ queryKey: ['artists', id] });
      qc.invalidateQueries({ queryKey: ['favorites'] });
    },
  });
};

export const useSetArtistTags = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, tags }: { id: number; tags: string[] }) => api.setArtistTags(id, tags),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: ['artists'] });
      qc.invalidateQueries({ queryKey: ['artists', id] });
      qc.invalidateQueries({ queryKey: ['tags'] });
    },
  });
};
