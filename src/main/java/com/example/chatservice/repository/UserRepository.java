package com.example.chatservice.repository;

import com.example.chatservice.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("""
            select u from User u
            where u.id <> :selfId
              and (lower(u.username) like lower(concat('%', :query, '%'))
                   or lower(u.nickname) like lower(concat('%', :query, '%')))
            order by u.username asc
            """)
    List<User> searchByUsernameOrNickname(@Param("query") String query, @Param("selfId") Long selfId, Pageable pageable);
}