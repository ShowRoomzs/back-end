package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;

import java.util.Optional;

public interface CreatorRepository extends JpaRepository<Creator, Long> {
    Optional<Creator> findByUser_Id(Long userId);

    Optional<Creator> findByUser(Users user);
}
