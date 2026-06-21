# AGENTS.md

This document provides guidelines for AI coding agents working in this repository.

## Project Overview

This is a Java-based backend service for a vocabulary/word learning application using Spring Boot 3.2.0, PostgreSQL, and Docker.

## Build Commands

```bash
# Build the project
./mvnw clean install

# Build without running tests
./mvnw clean install -DskipTests

# Compile only
./mvnw compile
```

## Test Commands

```bash
# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=UserServiceTest

# Run a single test method
./mvnw test -Dtest=UserServiceTest#testCreateUser

# Run tests with specific tags
./mvnw test -Dgroups=integration
```

## Lint and Code Quality

```bash
# Run checkstyle
./mvnw checkstyle:check

# Run all quality checks
./mvnw verify
```

## Docker Commands

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f app

# Rebuild and restart
docker-compose up -d --build

# Rebuild and restart the unified frontend after changes under frontend/
docker-compose up -d --build frontend
```

### Frontend Restart Requirement
- After modifying files under `frontend/`, rebuild and restart the unified `frontend` container with Docker before considering the change complete.
- Admin frontend code now lives under `frontend/admin/`; it is built and served by the same `frontend` container.

## Code Style Guidelines

### Imports
- Use explicit imports; avoid wildcard imports (`import java.util.*`)
- Order: `java.*`, `javax.*`, third-party, then project packages
- Static imports after regular imports

```java
// Good
import java.util.List;
import java.util.Optional;
import javax.persistence.Entity;
import org.springframework.stereotype.Service;
import com.example.words.model.Word;
import com.example.words.repository.WordRepository;
```

### Formatting
- 4 spaces for indentation (no tabs)
- Maximum line length: 120 characters
- Opening braces on same line
- Blank line between methods

### Naming Conventions
- **Classes/Interfaces**: PascalCase (`WordService`)
- **Methods**: camelCase (`findByName`)
- **Variables**: camelCase (`wordList`)
- **Constants**: SCREAMING_SNAKE_CASE
- **Packages**: lowercase
- **Database tables**: snake_case

### Types and Null Safety
- Prefer `Optional<T>` for return types
- Use primitive types when possible (`long` over `Long`)
- Use `LocalDateTime` for timestamps

### Error Handling
- Use custom exceptions for domain-specific errors
- Use `@ControllerAdvice` for global exception handling
- Log exceptions with context

### REST API Conventions
- Use plural nouns: `/api/dictionaries`
- Use HTTP methods correctly: GET, POST, PUT, DELETE
- Return appropriate status codes

### Database and JPA
- Use constructor injection
- Use `@Transactional` on service methods that modify data
- Follow Spring Data JPA naming conventions

### Logging
- Use SLF4J with `@Slf4j` annotation
- Log at appropriate levels: debug, info, warn, error
- Never log sensitive information

### Entity Classes (Lombok)
- Use Lombok annotations to reduce boilerplate code
- DO NOT write manual getters/setters/constructors

```java
// Good - use Lombok
@Entity
@Table(name = "meta_words")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaWord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "word", nullable = false)
    private String word;

    private String phonetic;
    private String definition;
    private Integer difficulty;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- `@Data` - generates getters, setters, equals, hashCode, toString
- `@NoArgsConstructor` - generates no-arg constructor
- `@AllArgsConstructor` - generates constructor with all fields
- `@Builder` - for builder pattern
- `@Getter` / `@Setter` - generate only getters or setters

## File Organization

```
src/main/java/com/example/words/
├── controller/     # REST controllers
├── service/       # Business logic
├── repository/    # Data access
├── model/         # JPA entities
├── dto/           # Data transfer objects
├── config/        # Configuration classes
└── exception/     # Custom exceptions
```

## API Endpoints

### Dictionary (辞书)

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/dictionaries | List all dictionaries |
| GET | /api/dictionaries/{id} | Get dictionary by ID |
| GET | /api/dictionaries/category/{category} | Get by category |
| POST | /api/dictionaries/import | Import dictionaries from directory |
| DELETE | /api/dictionaries | Delete all |

### MetaWord (元单词)

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/meta-words | List all meta words |
| GET | /api/meta-words/{id} | Get meta word by ID |
| GET | /api/meta-words/word/{word} | Get meta word by word |
| GET | /api/meta-words/search?prefix=xxx | Search words by prefix |
| GET | /api/meta-words/difficulty/{difficulty} | Get words by difficulty |
| POST | /api/meta-words | Create meta word |
| POST | /api/meta-words/import | Import words from books directory |
| DELETE | /api/meta-words | Delete all |

### DictionaryWord (辞书单词关联)

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/dictionary-words/dictionary/{dictionaryId} | Get words in dictionary |
| GET | /api/dictionary-words/word/{metaWordId} | Get dictionaries containing word |
| POST | /api/dictionary-words/{dictionaryId}/{metaWordId} | Add word to dictionary |
| DELETE | /api/dictionary-words/dictionary/{dictionaryId} | Remove all words from dictionary |
| DELETE | /api/dictionary-words | Delete all associations |
