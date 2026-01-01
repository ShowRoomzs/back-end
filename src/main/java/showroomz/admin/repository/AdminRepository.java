package showroomz.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import showroomz.admin.entity.Admins;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admins, Long> {
    Optional<Admins> findByEmail(String email);
    boolean existsByEmail(String email);
}

