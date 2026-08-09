package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;

import java.util.List;
import java.util.Optional;

public interface CreatorRepository extends JpaRepository<Creator, Long> {
    Optional<Creator> findByUser_Id(Long userId);

    Optional<Creator> findByUser(Users user);

    boolean existsByShowroomName(String showroomName);

    boolean existsByShowroomNameAndIdNot(String showroomName, Long id);

    Optional<Creator> findByConnectionCode(String connectionCode);

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
}
