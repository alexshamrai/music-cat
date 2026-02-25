package io.github.alexshamrai.repository;

import io.github.alexshamrai.domain.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<TagEntity, Long> {

    Optional<TagEntity> findByName(String name);

    List<TagEntity> findByNameIn(Collection<String> names);

}
