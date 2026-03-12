package ro.timetable.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Long> {
    boolean existsByUsername(String username);
    Optional<UserProfileEntity> findByUsername(String username);
}
