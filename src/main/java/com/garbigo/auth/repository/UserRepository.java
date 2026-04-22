package com.garbigo.auth.repository;

import com.garbigo.auth.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    @Query("{ $or: [ { email: { $regex: ?0, $options: 'i' } }, { firstName: { $regex: ?0, $options: 'i' } }, { lastName: { $regex: ?0, $options: 'i' } } ] }")
    List<User> searchUsers(String keyword);
}