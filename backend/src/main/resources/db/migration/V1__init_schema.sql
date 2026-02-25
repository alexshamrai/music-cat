CREATE TABLE artist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    genre VARCHAR(100) NOT NULL,
    subgenre VARCHAR(100),
    is_favorite BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE album (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    "year" INTEGER,
    grade INTEGER,
    is_favorite BOOLEAN DEFAULT FALSE,
    artist_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_grade CHECK (grade BETWEEN 1 AND 5),
    CONSTRAINT fk_album_artist FOREIGN KEY (artist_id) REFERENCES artist(id) ON DELETE CASCADE
);

CREATE TABLE song (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    track_number INTEGER NOT NULL,
    disc_number INTEGER DEFAULT 1,
    album_id BIGINT NOT NULL,
    CONSTRAINT fk_song_album FOREIGN KEY (album_id) REFERENCES album(id) ON DELETE CASCADE
);

CREATE TABLE tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE artist_tags (
    artist_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (artist_id, tag_id),
    CONSTRAINT fk_artist_tags_artist FOREIGN KEY (artist_id) REFERENCES artist(id) ON DELETE CASCADE,
    CONSTRAINT fk_artist_tags_tag FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);

CREATE TABLE album_tags (
    album_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (album_id, tag_id),
    CONSTRAINT fk_album_tags_album FOREIGN KEY (album_id) REFERENCES album(id) ON DELETE CASCADE,
    CONSTRAINT fk_album_tags_tag FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);

CREATE INDEX idx_artist_genre ON artist(genre);
CREATE INDEX idx_artist_name ON artist(name);
CREATE INDEX idx_album_artist_id ON album(artist_id);
CREATE INDEX idx_album_grade ON album(grade);
CREATE INDEX idx_song_album_id ON song(album_id);
