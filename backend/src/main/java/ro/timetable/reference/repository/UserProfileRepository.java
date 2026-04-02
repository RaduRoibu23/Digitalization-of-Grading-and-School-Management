package ro.timetable.reference.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.timetable.reference.entity.UserProfileEntity;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Long> {
    boolean existsByUsername(String username);
    Optional<UserProfileEntity> findByUsername(String username);
    boolean existsByEmailAndUsernameNot(String email, String username);
    boolean existsByCnpAndUsernameNot(String cnp, String username);
    boolean existsByIdSeriesAndSerialNumberAndUsernameNot(String idSeries, String serialNumber, String username);
}
