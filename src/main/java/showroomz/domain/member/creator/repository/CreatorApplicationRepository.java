package showroomz.domain.member.creator.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;

import java.util.List;
import java.util.Optional;

public interface CreatorApplicationRepository extends JpaRepository<CreatorApplication, Long> {

    boolean existsByUser_IdAndStatus(Long userId, CreatorApplicationStatus status);

    Optional<CreatorApplication> findTopByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<CreatorApplication> findTopByUser_IdAndStatusOrderByCreatedAtDesc(
            Long userId, CreatorApplicationStatus status);

    @Query(value = "select ca from CreatorApplication ca join fetch ca.user",
            countQuery = "select count(ca) from CreatorApplication ca")
    Page<CreatorApplication> findAllWithUser(Pageable pageable);

    @Query("select ca from CreatorApplication ca join fetch ca.user where ca.id = :applicationId")
    Optional<CreatorApplication> findByIdWithUser(@Param("applicationId") Long applicationId);

    /**
     * 크리에이터 지원서 목록 조회 (상태 필터 + 활동명/계정아이디 통합 검색)
     */
    @Query(
            value = "SELECT ca FROM CreatorApplication ca JOIN FETCH ca.user u " +
                    "WHERE (:status IS NULL OR ca.status = :status) " +
                    "AND (:keyword IS NULL OR :keyword = '' " +
                    "     OR u.nickname LIKE CONCAT('%', :keyword, '%') " +
                    "     OR ca.accountId LIKE CONCAT('%', :keyword, '%'))",
            countQuery = "SELECT COUNT(ca) FROM CreatorApplication ca JOIN ca.user u " +
                    "WHERE (:status IS NULL OR ca.status = :status) " +
                    "AND (:keyword IS NULL OR :keyword = '' " +
                    "     OR u.nickname LIKE CONCAT('%', :keyword, '%') " +
                    "     OR ca.accountId LIKE CONCAT('%', :keyword, '%'))"
    )
    Page<CreatorApplication> search(
            @Param("status") CreatorApplicationStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 상태별 건수 (검색어 반영, 상태 필터 미반영)
     */
    @Query("SELECT ca.status, COUNT(ca) FROM CreatorApplication ca JOIN ca.user u " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "       OR u.nickname LIKE CONCAT('%', :keyword, '%') " +
           "       OR ca.accountId LIKE CONCAT('%', :keyword, '%')) " +
           "GROUP BY ca.status")
    List<Object[]> countByStatus(@Param("keyword") String keyword);
}
