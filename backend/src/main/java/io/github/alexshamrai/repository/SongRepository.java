package io.github.alexshamrai.repository;

import io.github.alexshamrai.domain.SongEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongRepository extends JpaRepository<SongEntity, Long> {

}
