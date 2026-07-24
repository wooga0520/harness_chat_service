package com.example.chatservice.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for repository-slice tests: real (containerized) Postgres, no Redis/web/security
 * context. Use for exercising custom JPQL that an in-memory or mocked DB would let slide.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public abstract class AbstractRepositoryTest implements Containers {
}
