// Genre display names, matching the backend Genre enum (io.github.alexshamrai.domain.Genre).
// Used for the artist genre dropdown; sent verbatim as the genre value (the backend
// deserializes by display name via @JsonCreator).
export const GENRES = [
  'Progressive Rock',
  'Blues',
  'Instrumental Guitar',
  'Hard Rock & Metal',
  'Jazz & Funk',
  'Pop & Rock',
  'Soundtracks & Musicals',
  'Classical Music',
] as const;
