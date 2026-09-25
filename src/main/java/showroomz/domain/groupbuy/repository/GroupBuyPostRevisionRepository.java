package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.groupbuy.entity.GroupBuyPostRevision;

import java.util.List;

/** append-only — 수정·삭제 메서드를 두지 않는다(31 설계 2-6). 스튜디오 API는 읽지 않는다 — 어드민 설계가 읽는다. */
public interface GroupBuyPostRevisionRepository extends JpaRepository<GroupBuyPostRevision, Long> {

    @Query("SELECT COALESCE(MAX(r.revisionNo), 0) FROM GroupBuyPostRevision r WHERE r.groupBuyPost.postId = :postId")
    int findLastRevisionNo(@Param("postId") Long postId);

    List<GroupBuyPostRevision> findByGroupBuyPostPostIdOrderByRevisionNoAsc(Long postId);
}
