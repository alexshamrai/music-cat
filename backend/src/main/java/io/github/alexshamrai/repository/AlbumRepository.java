package io.github.alexshamrai.repository;

import io.github.alexshamrai.domain.AlbumEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<AlbumEntity, Long>, JpaSpecificationExecutor<AlbumEntity> {

    boolean existsByArtistIdAndTitle(Long artistId, String title);

    @Override
    @EntityGraph(attributePaths = {"tags", "artist"})
    List<AlbumEntity> findAll(Specification<AlbumEntity> spec);

    @Override
    @EntityGraph(attributePaths = {"tags", "artist", "songs"})
    Optional<AlbumEntity> findById(Long id);

    /** Loads all albums with their artist and tags in a single join — used by SheetSyncService. */
    @EntityGraph(attributePaths = {"artist", "tags"})
    @Query("select distinct a from AlbumEntity a")
    List<AlbumEntity> findAllForSync();
}
