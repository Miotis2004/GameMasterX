package com.gamemasterx.server.admin.repository;

import com.gamemasterx.server.admin.model.AdminDto;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends MongoRepository<AdminDto, String> {
}
