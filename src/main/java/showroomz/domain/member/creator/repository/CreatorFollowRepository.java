package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.user.entity.Users;

import java.util.List;

public interface CreatorFollowRepository extends JpaRepository<CreatorFollow, Long> {
    boolean existsByUserAndCreator(Users user, Creator creator);
    void deleteByUserAndCreator(Users user, Creator creator);
    List<CreatorFollow> findByUser(Users user); // 서비스 단 병합을 위해 List 반환
}
