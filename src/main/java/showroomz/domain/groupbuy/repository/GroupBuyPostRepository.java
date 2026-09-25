package showroomz.domain.groupbuy.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.groupbuy.entity.GroupBuyPost;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupBuyPostRepository extends JpaRepository<GroupBuyPost, Long> {

    @Query("SELECT gp FROM GroupBuyPost gp JOIN FETCH gp.post WHERE gp.groupBuy.id = :groupBuyId")
    Optional<GroupBuyPost> findByGroupBuyId(@Param("groupBuyId") Long groupBuyId);

    /**
     * 게시물 쓰기의 행 잠금(31 설계 2-7). 스튜디오의 임시저장·제출·수정과 어드민의 승인·반려·숨김·해제가 이 잠금을
     * 탄다 — 제출이 PENDING으로 올린 뒤 늦게 도착한 임시저장이 심사 중인 글을 덮지 못하게 한다.
     *
     * <p><b>잠금 순서는 {@code group_buy} → {@code group_buy_post}</b>다. 호출자는 공구 행을 먼저 잠근다 —
     * 순서가 뒤집힌 경로가 하나라도 있으면 교착이 난다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT gp FROM GroupBuyPost gp JOIN FETCH gp.post WHERE gp.groupBuy.id = :groupBuyId")
    Optional<GroupBuyPost> findByGroupBuyIdForUpdate(@Param("groupBuyId") Long groupBuyId);

    /** 목록의 게시물 상태 열 — 페이지 단위로 한 번에 모은다(설계서 4-2 N+1 주의). */
    @Query("SELECT gp FROM GroupBuyPost gp WHERE gp.groupBuy.id IN :groupBuyIds")
    List<GroupBuyPost> findByGroupBuyIds(@Param("groupBuyIds") Collection<Long> groupBuyIds);
}
