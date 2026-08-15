package showroomz.domain.terms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.repository.TermsVersionRepository;
import showroomz.domain.terms.type.TermsVersionStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시행 전환 (기획 §21-6) — 시행일 00:00에 새 버전을 시행중으로 올리고 기존 버전을 과거 버전으로 내린다.
 *
 * <p>프론트에서 날짜를 비교해 표시만 바꾸지 않는 이유 — 화면과 서버 데이터가 어긋난다. 소비자에게
 * 어떤 버전이 시행 중인지는 동의 기록이 참조하는 값이라 <b>서버에 저장된 상태가 유일한 근거</b>여야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TermsEffectuationService {

    private final TermsVersionRepository termsVersionRepository;

    /**
     * 시행일이 지난 시행 예정 버전을 전환한다. 여러 번 실행해도 결과가 같다(멱등).
     *
     * @return 시행중으로 전환된 버전 수
     */
    @Transactional
    public int effectuateDueVersions(LocalDate today) {
        List<TermsVersion> dueVersions = termsVersionRepository
                .findAllByStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateAscIdAsc(
                        TermsVersionStatus.SCHEDULED, today);

        if (dueVersions.isEmpty()) {
            return 0;
        }

        Map<Long, List<TermsVersion>> byDocument = new LinkedHashMap<>();
        for (TermsVersion version : dueVersions) {
            byDocument.computeIfAbsent(version.getDocument().getId(), id -> new ArrayList<>())
                    .add(version);
        }

        int effectuated = 0;
        for (Map.Entry<Long, List<TermsVersion>> entry : byDocument.entrySet()) {
            effectuated += effectuateDocument(entry.getKey(), entry.getValue());
        }
        return effectuated;
    }

    /**
     * 한 문서의 전환. 배치가 실행되지 못한 사이에 시행일이 둘 이상 지났다면 <b>가장 늦은 시행일의
     * 버전만</b> 시행중이 되고, 그 사이 버전들은 시행된 적 없이 곧바로 과거 버전이 된다 —
     * 이미 지난 기간을 뒤늦게 "시행중"으로 표시하면 동의 기록과 어긋난다.
     */
    private int effectuateDocument(Long documentId, List<TermsVersion> dueVersions) {
        TermsVersion latest = dueVersions.get(dueVersions.size() - 1);

        for (int i = 0; i < dueVersions.size() - 1; i++) {
            TermsVersion skipped = dueVersions.get(i);
            skipped.expire();
            log.warn("약관 시행 전환 - 시행일을 건너뛴 버전을 과거 버전으로 처리 - documentId={}, version={}, effectiveDate={}",
                    documentId, skipped.getVersionNumber(), skipped.getEffectiveDate());
        }

        termsVersionRepository.findAllByDocumentIdAndStatus(documentId, TermsVersionStatus.EFFECTIVE)
                .forEach(TermsVersion::expire);

        latest.takeEffect();
        log.info("약관 시행 전환 - documentId={}, version={}, effectiveDate={}",
                documentId, latest.getVersionNumber(), latest.getEffectiveDate());
        return 1;
    }
}
