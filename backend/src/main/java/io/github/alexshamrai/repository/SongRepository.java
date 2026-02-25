package io.github.alexshamrai.repository;

import io.github.alexshamrai.domain.Song;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongRepository extends JpaRepository<Song, Long> {

}
