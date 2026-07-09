import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from '../api/client';
import type { AlbumCreateDto, AlbumEditDto, AlbumFilterParams, AlbumUpdateDto } from '../types';

export const useAlbums = (filters?: AlbumFilterParams) =>
  useQuery({
    queryKey: ['albums', filters],
    queryFn: () => api.fetchAlbums(filters),
  });

export const useAlbum = (id: number) =>
  useQuery({
    queryKey: ['albums', id],
    queryFn: () => api.fetchAlbum(id),
    enabled: !!id,
  });

export const useCreateAlbum = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (dto: AlbumCreateDto) => api.createAlbum(dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['albums'] });
      qc.invalidateQueries({ queryKey: ['genres'] }); // Browse: per-genre counts + albums-by-artist
      qc.invalidateQueries({ queryKey: ['stats'] });
    },
  });
};

export const useUpdateAlbum = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dto }: { id: number; dto: AlbumUpdateDto }) => api.updateAlbum(id, dto),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: ['albums'] });
      qc.invalidateQueries({ queryKey: ['albums', id] });
      qc.invalidateQueries({ queryKey: ['genres'] }); // Browse albums-by-artist shows the title
    },
  });
};

export const useEditAlbum = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dto }: { id: number; dto: AlbumEditDto }) => api.editAlbum(id, dto),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: ['albums'] });
      qc.invalidateQueries({ queryKey: ['albums', id] });
      qc.invalidateQueries({ queryKey: ['genres'] }); // Browse albums-by-artist shows the title
      qc.invalidateQueries({ queryKey: ['stats'] });
    },
  });
};

export const useDeleteAlbum = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.deleteAlbum(id),
    // Invalidate album LIST queries only — not the deleted album's detail (['albums', <number>]),
    // which would otherwise refetch a now-404 resource while the detail page navigates away.
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['albums'], predicate: (q) => typeof q.queryKey[1] !== 'number' });
      qc.invalidateQueries({ queryKey: ['genres'] });
      qc.invalidateQueries({ queryKey: ['stats'] });
      qc.invalidateQueries({ queryKey: ['favorites'] });
    },
  });
};

export const useSetAlbumGrade = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, grade }: { id: number; grade: number }) => api.setAlbumGrade(id, grade),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: ['albums'] });
      qc.invalidateQueries({ queryKey: ['albums', id] });
      qc.invalidateQueries({ queryKey: ['stats'] });
    },
  });
};

export const useToggleAlbumFavorite = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.toggleAlbumFavorite(id),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: ['albums'] });
      qc.invalidateQueries({ queryKey: ['albums', id] });
      qc.invalidateQueries({ queryKey: ['favorites'] });
    },
  });
};

export const useSetAlbumTags = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, tags }: { id: number; tags: string[] }) => api.setAlbumTags(id, tags),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: ['albums'] });
      qc.invalidateQueries({ queryKey: ['albums', id] });
      qc.invalidateQueries({ queryKey: ['tags'] });
    },
  });
};
