package com.gamemasterx.server.adventure.repository;

import com.gamemasterx.server.adventure.model.Adventure;
import com.gamemasterx.server.adventure.model.Adventure.Status;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data MongoDB repository for the {@link Adventure} aggregate.
 *
 * <p>The collection name ({@code adventures}) is defined by the
 * {@link org.springframework.data.mongodb.core.mapping.Document} annotation on
 * {@link Adventure}. The {@code revision} counter on the aggregate is managed
 * automatically by Spring Data MongoDB for optimistic concurrency control.</p>
 */
@Repository
public interface AdventureRepository extends MongoRepository<Adventure, String> {

    List<Adventure> findByStatus(Status status);

    List<Adventure> findByGameSystem(String gameSystem);
}
