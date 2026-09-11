package com.gamemasterx.server.tactical.repository;

import com.gamemasterx.server.tactical.model.TacticalMap;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TacticalMapRepository extends MongoRepository<TacticalMap, String> {
}
