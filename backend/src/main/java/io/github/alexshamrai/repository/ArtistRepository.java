package io.github.alexshamrai.repository;

import java.util.Optional;

import io.github.alexshamrai.domain.ArtistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ArtistRepository extends JpaRepository<ArtistEntity, Long>, JpaSpecificationExecutor<ArtistEntity> {

    Optional<ArtistEntity> findByNameAndGenre(String name, String genre);
}
