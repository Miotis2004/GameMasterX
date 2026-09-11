package com.gamemasterx.server.user.repository;

import com.gamemasterx.server.user.model.UserDto;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends MongoRepository<UserDto, String> {
    java.util.Optional<UserDto> findByUsername(String username);
    boolean existsByGlobalAdminTrue();
}
