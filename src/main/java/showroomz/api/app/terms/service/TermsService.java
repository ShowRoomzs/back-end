package showroomz.api.app.terms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.terms.dto.TermsDocumentDetailResponse;
import showroomz.api.app.terms.dto.TermsDocumentResponse;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.repository.TermsVersionRepository;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;
import showroomz.domain.terms.type.TermsVersionStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Comparator;
import java.util.List;

/**
 * 소비자 문서 뷰어 (기획 C18) — 시행 중인 버전만 노출한다.
 *
 * <p>시행 예정 버전은 시행일 00:00에 배치가 전환하기 전까지 이 API로 내려가지 않는다 —
 * 프론트가 날짜를 비교해 미리 보여 주면 서버가 기록하는 "동의한 버전"과 어긋난다 (기획 §21-6).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsService {

    private final TermsVersionRepository termsVersionRepository;

    /**
     * 시행 중인 문서 목록.
     *
     * @param target 대상 필터 — 지정하면 해당 대상과 전체(ALL) 문서를 함께 내려준다.
     *               소비자 화면은 "전체 대상 문서 + 자기 대상 문서"를 함께 보여 주기 때문이다.
     */
    public List<TermsDocumentResponse> getTerms(TermsType type, TermsTarget target) {
        return termsVersionRepository.findAllByStatusWithDocument(TermsVersionStatus.EFFECTIVE).stream()
                .filter(version -> type == null || version.getDocument().getType() == type)
                .filter(version -> matchesTarget(version, target))
                .sorted(Comparator
                        .comparing((TermsVersion version) -> version.getDocument().getType().ordinal())
                        .thenComparing(version -> version.getDocument().getId()))
                .map(TermsDocumentResponse::from)
                .toList();
    }

    /** 시행 중인 버전이 없는 문서(시행 예정만 있거나 구버전)는 앱에서 접근할 수 없다 — 404로 응답한다. */
    public TermsDocumentDetailResponse getTermsDetail(Long documentId) {
        TermsVersion version = termsVersionRepository
                .findAllByDocumentIdAndStatus(documentId, TermsVersionStatus.EFFECTIVE).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA, "시행 중인 문서가 아닙니다."));

        return TermsDocumentDetailResponse.from(version);
    }

    private boolean matchesTarget(TermsVersion version, TermsTarget target) {
        if (target == null) {
            return true;
        }
        TermsTarget documentTarget = version.getDocument().getTarget();
        return documentTarget == target || documentTarget == TermsTarget.ALL;
    }
}
