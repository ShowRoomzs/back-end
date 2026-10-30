package showroomz.api.app.user.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import showroomz.domain.member.user.entity.Users;

@Repository
public interface UserRepository extends JpaRepository<Users, Long>, JpaSpecificationExecutor<Users> {
    Optional<Users> findByUsername(String username);

    /** 정지 · 해제가 회원 행을 잠근다(51-6 설계서 2절) — 두 운영자의 동시 조치를 한 줄로 세운다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Users u WHERE u.id = :userId")
    Optional<Users> findForUpdate(@Param("userId") Long userId);

    /** DI 중복 가입 차단(57 설계서 0-5) — 활성 회원만. GUEST 는 해시가 없고 탈퇴는 파기돼 조건이 사실상 UNIQUE 와 겹친다. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT u FROM Users u
            WHERE u.identityDiHash = :diHash AND u.roleType <> 'GUEST' AND u.status <> 'WITHDRAWN'
            """)
    Optional<Users> findActiveByIdentityDiHash(@Param("diHash") String diHash);

    Boolean existsByUsername(String username);
    Boolean existsByNickname(String nickname);
    Boolean existsByEmail(String email);
}
