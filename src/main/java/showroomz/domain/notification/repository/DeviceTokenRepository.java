package showroomz.domain.notification.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.notification.entity.DeviceToken;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    void deleteByToken(String token);

    /** C15-4 탈퇴 — 발송 대상 목록에서 지운다 */
    void deleteByUser(Users user);

    /**
     * FCM이 "없는 토큰"이라고 돌려준 것들을 정리한다.
     *
     * <p><b>{@code clearAutomatically}를 켜지 않는다.</b> 이 삭제는 발송 도중에 불리고, 발송을
     * 감싼 트랜잭션에는 갱신을 기다리는 {@code PostNotificationLog}가 올라와 있다. 영속성
     * 컨텍스트를 비우면 그 엔티티가 준영속이 되어 뒤이은 {@code markDelivered()}가 조용히
     * 사라진다 — 실제로는 발송됐는데 이력에는 미전달로 남는다.
     *
     * <p>비우지 않아도 안전한 이유 — 발송 경로는 {@code DeviceToken} 엔티티를 로드하지 않고
     * {@link DeviceTokenRow} 프로젝션만 읽는다. 지운 행이 컨텍스트에 남아 되살아날 여지가 없다.
     */
    @Modifying
    @Query("DELETE FROM DeviceToken dt WHERE dt.token IN :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);

    /**
     * 팔로워 신규 게시물 알림(§24-3)의 발송 대상.
     *
     * <p>세 가지를 <b>쿼리에서</b> 거른다 — 알림을 끈 사람({@code follow_post_push_agree}),
     * 탈퇴·정지 회원, 그리고 이미 처리한 구간({@code afterId}). 애플리케이션에서 거르면
     * 팔로워 수만 명인 쇼룸에서 필요 없는 행을 전부 읽게 된다.
     *
     * <p>{@code afterId} 키셋으로 끊어 읽는 이유는 {@link DeviceTokenRow} 주석 참고 —
     * 발송 중 만료 토큰을 지우므로 OFFSET 페이징은 대상을 건너뛴다.
     */
    @Query("SELECT new showroomz.domain.notification.repository.DeviceTokenRow(dt.id, dt.token) " +
           "FROM DeviceToken dt " +
           "JOIN CreatorFollow cf ON cf.user = dt.user " +
           "JOIN dt.user u " +
           "WHERE cf.creator.id = :creatorId " +
           "AND dt.id > :afterId " +
           "AND u.status = :status " +
           "AND u.notificationSetting.followPostPushAgree = true " +
           "ORDER BY dt.id ASC")
    List<DeviceTokenRow> findFollowerTokens(@Param("creatorId") Long creatorId,
                                            @Param("status") UserStatus status,
                                            @Param("afterId") Long afterId,
                                            Pageable pageable);

    /**
     * 본인 앞으로 가는 통지(노출 중지·이의 신청 결과 등)의 발송 대상.
     *
     * <p>여기에는 {@code follow_post_push_agree}를 보지 않는다. 그 토글은 "팔로우한 쇼룸의 새
     * 게시물"만 끄는 값이고, 내 게시물이 내려갔다는 통지는 끌 수 있는 알림이 아니다(§24-5).
     */
    @Query("SELECT new showroomz.domain.notification.repository.DeviceTokenRow(dt.id, dt.token) " +
           "FROM DeviceToken dt " +
           "WHERE dt.user.id = :userId AND dt.id > :afterId " +
           "ORDER BY dt.id ASC")
    List<DeviceTokenRow> findUserTokens(@Param("userId") Long userId,
                                        @Param("afterId") Long afterId,
                                        Pageable pageable);
}
