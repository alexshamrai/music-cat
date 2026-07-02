package io.github.alexshamrai.repository;

import io.github.alexshamrai.domain.SongEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SongRepository extends JpaRepository<SongEntity, Long> {

    /** Loads all songs with their album and album's artist in a single join — used by SheetSyncService. */
    @EntityGraph(attributePaths = {"album", "album.artist"})
    @Query("select distinct s from SongEntity s")
    List<SongEntity> findAllForSync();
}
