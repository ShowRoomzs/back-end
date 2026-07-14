package showroomz.domain.member.creator.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;

public interface CreatorApplicationRepository extends JpaRepository<CreatorApplication, Long> {

    boolean existsByUser_IdAndStatus(Long userId, CreatorApplicationStatus status);

    @Query(value = "select ca from CreatorApplication ca join fetch ca.user",
            countQuery = "select count(ca) from CreatorApplication ca")
    Page<CreatorApplication> findAllWithUser(Pageable pageable);
}
