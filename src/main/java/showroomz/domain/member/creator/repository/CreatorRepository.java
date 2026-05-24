package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.creator.entity.Creator;

import java.util.Optional;

public interface CreatorRepository extends JpaRepository<Creator, Long> {
    Optional<Creator> findByUser_Id(Long userId);
}
