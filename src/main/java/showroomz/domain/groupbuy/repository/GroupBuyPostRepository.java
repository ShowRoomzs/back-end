package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.groupbuy.entity.GroupBuyPost;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupBuyPostRepository extends JpaRepository<GroupBuyPost, Long> {

    @Query("SELECT gp FROM GroupBuyPost gp JOIN FETCH gp.post WHERE gp.groupBuy.id = :groupBuyId")
    Optional<GroupBuyPost> findByGroupBuyId(@Param("groupBuyId") Long groupBuyId);

    /** 목록의 게시물 상태 열 — 페이지 단위로 한 번에 모은다(설계서 4-2 N+1 주의). */
    @Query("SELECT gp FROM GroupBuyPost gp WHERE gp.groupBuy.id IN :groupBuyIds")
    List<GroupBuyPost> findByGroupBuyIds(@Param("groupBuyIds") Collection<Long> groupBuyIds);
}
