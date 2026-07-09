import axios from 'axios';
import type {
  Artist,
  ArtistCreateDto,
  ArtistUpdateDto,
  Album,
  AlbumSummary,
  AlbumCreateDto,
  AlbumUpdateDto,
  AlbumEditDto,
  AlbumFilterParams,
  GenreBrowse,
  TagBrowse,
  BrowseStats,
  BrowseFavorites,
  Tag,
} from '../types';

// X-Requested-With can't be set by a plain HTML <form>, so requiring it on the backend
// blocks blind cross-site form submissions to state-changing endpoints (see
// RequireXhrHeaderFilter) while every real request from this app sends it automatically.
const api = axios.create({ baseURL: '/api', headers: { 'X-Requested-With': 'XMLHttpRequest' } });

function toQueryParams(filters?: AlbumFilterParams): Record<string, string | number | boolean | string[]> {
  if (!filters) return {};
  const params: Record<string, string | number | boolean | string[]> = {};
  if (filters.genre != null) params.genre = filters.genre;
  if (filters.subgenre != null) params.subgenre = filters.subgenre;
  if (filters.artistId != null) params.artistId = filters.artistId;
  if (filters.artistName != null) params.artistName = filters.artistName;
  if (filters.tag != null && filters.tag.length > 0) params.tag = filters.tag;
  if (filters.minGrade != null) params.minGrade = filters.minGrade;
  if (filters.maxGrade != null) params.maxGrade = filters.maxGrade;
  if (filters.favorite != null) params.favorite = filters.favorite;
  if (filters.unrated != null) params.unrated = filters.unrated;
  return params;
}

// Artists
export const fetchArtists = async (filters?: { genre?: string; subgenre?: string; favorite?: boolean; tag?: string }) => {
  const { data } = await api.get<Artist[]>('/artists', { params: filters });
  return data;
};

export const fetchArtist = async (id: number) => {
  const { data } = await api.get<Artist>(`/artists/${id}`);
  return data;
};

export const createArtist = async (dto: ArtistCreateDto) => {
  const { data } = await api.post<Artist>('/artists', dto);
  return data;
};

export const updateArtist = async (id: number, dto: ArtistUpdateDto) => {
  const { data } = await api.put<Artist>(`/artists/${id}`, dto);
  return data;
};

export const deleteArtist = async (id: number) => {
  await api.delete(`/artists/${id}`);
};

export const toggleArtistFavorite = async (id: number) => {
  const { data } = await api.patch<Artist>(`/artists/${id}/favorite`);
  return data;
};

export const setArtistTags = async (id: number, tags: string[]) => {
  const { data } = await api.put<Artist>(`/artists/${id}/tags`, tags);
  return data;
};

// Albums
export const fetchAlbums = async (filters?: AlbumFilterParams) => {
  const { data } = await api.get<AlbumSummary[]>('/albums', { params: toQueryParams(filters) });
  return data;
};

export const fetchAlbum = async (id: number) => {
  const { data } = await api.get<Album>(`/albums/${id}`);
  return data;
};

export const createAlbum = async (dto: AlbumCreateDto) => {
  const { data } = await api.post<AlbumSummary>('/albums', dto);
  return data;
};

export const updateAlbum = async (id: number, dto: AlbumUpdateDto) => {
  const { data } = await api.put<AlbumSummary>(`/albums/${id}`, dto);
  return data;
};

export const deleteAlbum = async (id: number) => {
  await api.delete(`/albums/${id}`);
};

// Batch edit: album title/year + full desired song set (add/rename/delete), applied atomically.
export const editAlbum = async (id: number, dto: AlbumEditDto) => {
  const { data } = await api.put<Album>(`/albums/${id}/edit`, dto);
  return data;
};

export const setAlbumGrade = async (id: number, grade: number) => {
  const { data } = await api.patch<AlbumSummary>(`/albums/${id}/grade`, { grade });
  return data;
};

export const toggleAlbumFavorite = async (id: number) => {
  const { data } = await api.patch<AlbumSummary>(`/albums/${id}/favorite`);
  return data;
};

export const setAlbumTags = async (id: number, tags: string[]) => {
  const { data } = await api.put<AlbumSummary>(`/albums/${id}/tags`, tags);
  return data;
};

// Browse
export const fetchGenres = async () => {
  const { data } = await api.get<GenreBrowse[]>('/browse/genres');
  return data;
};

export const fetchArtistsByGenre = async (genre: string) => {
  const { data } = await api.get<Artist[]>(`/browse/genres/${encodeURIComponent(genre)}`);
  return data;
};

export const fetchAlbumsByArtist = async (genre: string, artistId: number) => {
  const { data } = await api.get<AlbumSummary[]>(`/browse/genres/${encodeURIComponent(genre)}/artists/${artistId}`);
  return data;
};

export const fetchTags = async () => {
  const { data } = await api.get<Tag[]>('/tags');
  return data;
};

export const fetchTagStats = async () => {
  const { data } = await api.get<TagBrowse[]>('/browse/tags');
  return data;
};

export const createTag = async (name: string) => {
  const { data } = await api.post<Tag>('/tags', { name });
  return data;
};

export const deleteTag = async (id: number) => {
  await api.delete(`/tags/${id}`);
};

export const fetchFavorites = async () => {
  const { data } = await api.get<BrowseFavorites>('/browse/favorites');
  return data;
};

export const fetchStats = async () => {
  const { data } = await api.get<BrowseStats>('/browse/stats');
  return data;
};

// Random
export const fetchRandomAlbum = async (filters?: AlbumFilterParams) => {
  const { data } = await api.get<Album>('/random/album', { params: toQueryParams(filters) });
  return data;
};

export const fetchRandomAlbums = async (filters?: AlbumFilterParams, count = 5) => {
  const { data } = await api.get<Album[]>('/random/albums', {
    params: { ...toQueryParams(filters), count },
  });
  return data;
};
