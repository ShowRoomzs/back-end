package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;

import java.util.List;
import java.util.Optional;

public interface CreatorRepository extends JpaRepository<Creator, Long> {
    Optional<Creator> findByUser_Id(Long userId);

    Optional<Creator> findByUser(Users user);

    boolean existsByShowroomName(String showroomName);

    boolean existsByShowroomNameAndIdNot(String showroomName, Long id);

    /** §22-1 쇼룸 주소는 쇼룸 사이에서 유일해야 한다(자동 생성 시 충돌 회피용). */
    boolean existsByShowroomAddress(String showroomAddress);

    Optional<Creator> findByConnectionCode(String connectionCode);

    /** PG 지급대행 셀러 id 로 — 웹훅 {@code seller.changed}(44 토스 설계서 4-3). */
    Optional<Creator> findByPgPartnerId(String pgPartnerId);

    boolean existsByConnectionCode(String connectionCode);

    /**
     * V91 백필이 채운 결정적 연결코드(`SZ` + CREATOR_ID 8자리 0패딩)를 가진 행. 이 코드는 순차 열거가
     * 가능하므로 기동 시 랜덤 코드로 재발급한다(LegacyConnectionCodeReissuer).
     *
     * <p>`LIKE 'SZ%'`가 아니라 <b>생성 규칙과의 정확한 등식</b>으로 판정하는 이유: 랜덤 코드 알파벳에도
     * `S`·`Z`가 있어 접두사만 보면 정상 코드를 1/1024 확률로 오탐한다.
     */
    // Flyway는 creator(소문자)로 생성한다. Linux CI(MySQL lower_case_table_names=0)에서는
    // CREATOR(대문자) 조회가 SQLSyntaxErrorException으로 실패하므로 소문자로 맞춘다.
    @Query(value = "SELECT creator_id FROM creator " +
                   "WHERE connection_code = CONCAT('SZ', LPAD(creator_id, 8, '0'))", nativeQuery = true)
    List<Long> findIdsWithLegacyConnectionCode();

    /**
     * 조치(정지 · 해제 · 탈퇴 승인)의 직렬화(53 설계서 4-1) — 같은 인플루언서에 대한 동시 조치를 줄 세운다. 잠근 뒤 상태를 다시 보고
     * 엔티티로 바꾼다(조건부 UPDATE 와 같은 효과 · 관리 중인 엔티티가 벌크 UPDATE 를 덮어쓰지 않게).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Creator c JOIN FETCH c.user WHERE c.id = :creatorId")
    Optional<Creator> findForUpdate(@Param("creatorId") Long creatorId);

    @Query("SELECT c FROM Creator c JOIN FETCH c.user WHERE c.id = :creatorId")
    Optional<Creator> findWithUserById(@Param("creatorId") Long creatorId);

    /** 어드민 상세의 「이전 · 다음」 — 회원번호 순 인접 인플루언서(55 계획서 M4). 행은 입점 승인 때만 생기므로 필터가 없다. */
    @Query("SELECT MAX(c.id) FROM Creator c WHERE c.id < :id")
    Long findPreviousMemberId(@Param("id") Long id);

    @Query("SELECT MIN(c.id) FROM Creator c WHERE c.id > :id")
    Long findNextMemberId(@Param("id") Long id);

    /** 계좌 확인 재시도 대상(50 설계서 6-4) — 「확인 중」으로 남은 인플루언서. */
    @Query("SELECT c.id FROM Creator c WHERE c.accountVerificationStatus = :status ORDER BY c.id ASC")
    List<Long> findIdsByAccountVerificationStatus(
            @Param("status") showroomz.domain.member.creator.type.CreatorAccountVerificationStatus status,
            org.springframework.data.domain.Pageable pageable);
}
