package showroomz.domain.terms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.domain.terms.entity.TermsDocument;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.repository.TermsVersionRepository;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;
import showroomz.domain.terms.type.TermsVersionStatus;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TermsEffectuationServiceTest {

    private static final long DOCUMENT_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    @Mock
    private TermsVersionRepository termsVersionRepository;

    @InjectMocks
    private TermsEffectuationService termsEffectuationService;

    private final TermsDocument document = document();

    @Test
    @DisplayName("시행일이 도래하면 새 버전이 시행중이 되고 기존 시행 버전은 과거 버전이 된다")
    void effectuatesDueVersion() {
        TermsVersion current = version(1L, "3.1", LocalDate.of(2026, 6, 1), TermsVersionStatus.EFFECTIVE);
        TermsVersion due = version(2L, "3.2", TODAY, TermsVersionStatus.SCHEDULED);

        given(termsVersionRepository
                .findAllByStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateAscIdAsc(
                        eq(TermsVersionStatus.SCHEDULED), any(LocalDate.class)))
                .willReturn(List.of(due));
        given(termsVersionRepository.findAllByDocumentIdAndStatus(DOCUMENT_ID, TermsVersionStatus.EFFECTIVE))
                .willReturn(List.of(current));

        int effectuated = termsEffectuationService.effectuateDueVersions(TODAY);

        assertThat(effectuated).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(TermsVersionStatus.EFFECTIVE);
        // 과거 버전은 상태만 바뀌고 삭제되지 않는다 — 동의 기록이 참조한다
        assertThat(current.getStatus()).isEqualTo(TermsVersionStatus.PAST);
    }

    @Test
    @DisplayName("배치를 건너뛴 사이 시행일이 둘 지났다면 가장 늦은 버전만 시행중이 된다")
    void effectuatesOnlyLatestWhenRunsWereMissed() {
        TermsVersion current = version(1L, "3.1", LocalDate.of(2026, 6, 1), TermsVersionStatus.EFFECTIVE);
        TermsVersion skipped = version(2L, "3.2", LocalDate.of(2026, 8, 20), TermsVersionStatus.SCHEDULED);
        TermsVersion due = version(3L, "3.3", TODAY, TermsVersionStatus.SCHEDULED);

        given(termsVersionRepository
                .findAllByStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateAscIdAsc(
                        eq(TermsVersionStatus.SCHEDULED), any(LocalDate.class)))
                .willReturn(List.of(skipped, due));
        given(termsVersionRepository.findAllByDocumentIdAndStatus(DOCUMENT_ID, TermsVersionStatus.EFFECTIVE))
                .willReturn(List.of(current));

        int effectuated = termsEffectuationService.effectuateDueVersions(TODAY);

        assertThat(effectuated).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(TermsVersionStatus.EFFECTIVE);
        // 이미 지난 기간을 뒤늦게 "시행중"으로 표시하지 않는다
        assertThat(skipped.getStatus()).isEqualTo(TermsVersionStatus.PAST);
        assertThat(current.getStatus()).isEqualTo(TermsVersionStatus.PAST);
    }

    @Test
    @DisplayName("시행일이 도래한 버전이 없으면 아무 것도 바꾸지 않는다")
    void doesNothingWhenNoDueVersion() {
        given(termsVersionRepository
                .findAllByStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateAscIdAsc(
                        eq(TermsVersionStatus.SCHEDULED), any(LocalDate.class)))
                .willReturn(List.of());

        assertThat(termsEffectuationService.effectuateDueVersions(TODAY)).isZero();
    }

    private static TermsDocument document() {
        TermsDocument document = TermsDocument.builder()
                .name("소비자 이용약관")
                .type(TermsType.TERMS_OF_SERVICE)
                .target(TermsTarget.USER)
                .registeredBy(1L)
                .build();
        ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
        return document;
    }

    private TermsVersion version(long id, String versionNumber, LocalDate effectiveDate, TermsVersionStatus status) {
        TermsVersion version = TermsVersion.builder()
                .document(document)
                .versionNumber(versionNumber)
                .effectiveDate(effectiveDate)
                .content("제1조(목적) ...")
                .registeredBy(1L)
                .build();
        ReflectionTestUtils.setField(version, "id", id);
        ReflectionTestUtils.setField(version, "status", status);
        return version;
    }
}
