package io.github.alexshamrai.repository;

import io.github.alexshamrai.domain.AlbumEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlbumRepository extends JpaRepository<AlbumEntity, Long>, JpaSpecificationExecutor<AlbumEntity> {

    boolean existsByArtistIdAndTitle(Long artistId, String title);
}
