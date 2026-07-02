package io.github.alexshamrai.repository;

import java.util.List;
import java.util.Optional;

import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ArtistRepository extends JpaRepository<ArtistEntity, Long>, JpaSpecificationExecutor<ArtistEntity> {

    Optional<ArtistEntity> findByNameAndGenre(String name, Genre genre);

    @Override
    @EntityGraph(attributePaths = {"tags"})
    List<ArtistEntity> findAll(Specification<ArtistEntity> spec);

    @Override
    @EntityGraph(attributePaths = {"tags", "albums"})
    Optional<ArtistEntity> findById(Long id);

    /** Loads all artists with their tags in a single join — used by SheetSyncService. */
    @EntityGraph(attributePaths = {"tags"})
    @Query("select distinct a from ArtistEntity a")
    List<ArtistEntity> findAllForSync();
}
