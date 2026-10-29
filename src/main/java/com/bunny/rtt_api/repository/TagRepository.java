package com.bunny.rtt_api.repository;

import com.bunny.rtt_api.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
    Optional<Tag> findByName(String name);
    Optional<Tag> findByNameIgnoreCase(String name);
    Optional<Tag> findBySeedKey(String seedKey);
    boolean existsByName(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsBySeedKey(String seedKey);
}
