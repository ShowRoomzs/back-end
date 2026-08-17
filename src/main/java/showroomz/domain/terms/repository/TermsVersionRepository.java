package showroomz.domain.terms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.type.TermsVersionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TermsVersionRepository extends JpaRepository<TermsVersion, Long> {

    /** 문서 상세의 버전 이력 — 최신 시행일이 위로 온다 (기획 §21-4) */
    List<TermsVersion> findAllByDocumentIdOrderByEffectiveDateDescIdDesc(Long documentId);

    /** 목록의 표시 버전을 문서별로 한 번에 모은다 — 행마다 조회하면 N+1이 된다 */
    List<TermsVersion> findAllByDocumentIdInOrderByEffectiveDateAscIdAsc(List<Long> documentIds);

    List<TermsVersion> findAllByDocumentIdAndStatus(Long documentId, TermsVersionStatus status);

    Optional<TermsVersion> findFirstByDocumentIdAndStatusOrderByEffectiveDateAscIdAsc(
            Long documentId, TermsVersionStatus status);

    Optional<TermsVersion> findFirstByDocumentIdOrderByEffectiveDateDescIdDesc(Long documentId);

    List<TermsVersion> findAllByDocumentId(Long documentId);

    boolean existsByDocumentIdAndVersionNumber(Long documentId, String versionNumber);

    /** 시행 전환 배치 — 시행일이 도래한 시행 예정 버전 (기획 §21-6) */
    List<TermsVersion> findAllByStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateAscIdAsc(
            TermsVersionStatus status, LocalDate date);

    /** 소비자 화면 — 시행중 버전만 노출한다. 문서 정보를 함께 쓰므로 조인해 가져온다 (기획 §21-6) */
    @Query("select v from TermsVersion v join fetch v.document d where v.status = :status")
    List<TermsVersion> findAllByStatusWithDocument(@Param("status") TermsVersionStatus status);
}
