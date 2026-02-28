import { useQuery } from '@tanstack/react-query';
import * as api from '../api/client';
import type { AlbumFilterParams } from '../types';

export const useRandomAlbum = (filters?: AlbumFilterParams, enabled = true) =>
  useQuery({
    queryKey: ['randomAlbum', filters],
    queryFn: () => api.fetchRandomAlbum(filters),
    enabled,
    staleTime: 0,
    gcTime: 0,
  });
