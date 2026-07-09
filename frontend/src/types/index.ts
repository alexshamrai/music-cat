export interface Song {
  id: number;
  title: string;
  trackNumber: number;
  discNumber?: number;
}

export interface Tag {
  id: number;
  name: string;
}

export interface ArtistSummary {
  id: number;
  name: string;
  genre: string;
}

export interface Artist {
  id: number;
  name: string;
  genre: string;
  subgenre?: string;
  isFavorite: boolean;
  tags: string[];
  albumCount: number;
}

export interface AlbumSummary {
  id: number;
  title: string;
  year?: number;
  grade?: number;
  isFavorite: boolean;
  artistName: string;
  genre: string;
  tags: string[];
  songCount: number;
}

export interface Album {
  id: number;
  title: string;
  year?: number;
  grade?: number;
  isFavorite: boolean;
  artist: ArtistSummary;
  tags: string[];
  songs: Song[];
}

export interface GenreBrowse {
  genre: string;
  artistCount: number;
  albumCount: number;
}

export interface TagBrowse {
  tag: string;
  artistCount: number;
  albumCount: number;
}

export interface BrowseStats {
  totalArtists: number;
  totalAlbums: number;
  totalSongs: number;
  totalTags: number;
  totalGenres: number;
  favoriteArtists: number;
  favoriteAlbums: number;
  ratedAlbums: number;
  unratedAlbums: number;
  gradeDistribution: Record<string, number>;
}

export interface BrowseFavorites {
  favoriteArtists: Artist[];
  favoriteAlbums: AlbumSummary[];
}

export interface ImportResult {
  artistCount: number;
  albumCount: number;
  songCount: number;
}

export interface AlbumFilterParams {
  genre?: string;
  subgenre?: string;
  artistId?: number;
  artistName?: string;
  tag?: string[];
  minGrade?: number;
  maxGrade?: number;
  favorite?: boolean;
  unrated?: boolean;
}

export interface ArtistCreateDto {
  name: string;
  genre: string;
  subgenre?: string;
}

export interface ArtistUpdateDto {
  name?: string;
  genre?: string;
  subgenre?: string;
}

export interface AlbumCreateDto {
  title: string;
  year?: number;
  artistId: number;
}

export interface AlbumUpdateDto {
  title?: string;
  year?: number;
}

export interface SongEditInput {
  id: number | null;
  title: string;
}

export interface AlbumEditDto {
  title: string;
  year?: number | null;
  songs: SongEditInput[];
}
