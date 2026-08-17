package showroomz.api.app.terms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.terms.dto.TermsDocumentResponse;
import showroomz.domain.terms.entity.TermsDocument;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.repository.TermsVersionRepository;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;
import showroomz.domain.terms.type.TermsVersionStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 소비자 문서 뷰어 (C18) — <b>시행 중인 버전만</b> 내려간다.
 *
 * <p>시행 예정 버전은 시행일 00:00에 배치가 전환하기 전까지 이 API로 나가지 않는다. 프론트가 날짜를
 * 비교해 미리 보여 주면 서버가 기록하는 "동의한 버전"과 어긋난다 (§21-6) — 그 경계를 여기서 지킨다.
 *
 * <p>대상 필터는 "해당 대상 + 전체(ALL)"를 함께 내려주는 합집합이다. 소비자 화면이 공통 문서와
 * 자기 대상 문서를 한 목록에 보여 주기 때문이고, ALL이 빠지면 필수 약관이 목록에서 사라진다.
 */
@ExtendWith(MockitoExtension.class)
class TermsServiceTest {

    private static final long DOCUMENT_ID = 11L;

    @Mock
    private TermsVersionRepository termsVersionRepository;

    @InjectMocks
    private TermsService termsService;

    private TermsVersion version(long documentId, String name, TermsType type, TermsTarget target,
                                 TermsVersionStatus status) {
        TermsDocument document = TermsDocument.builder()
                .name(name)
                .type(type)
                .target(target)
                .registeredBy(1L)
                .build();
        ReflectionTestUtils.setField(document, "id", documentId);

        TermsVersion version = TermsVersion.builder()
                .document(document)
                .versionNumber("1.0")
                .effectiveDate(LocalDate.of(2026, 1, 1))
                .content("제1조(목적) ...")
                .registeredBy(1L)
                .build();
        ReflectionTestUtils.setField(version, "id", documentId * 100);
        ReflectionTestUtils.setField(version, "status", status);
        return version;
    }

    private TermsVersion effective(long documentId, String name, TermsType type, TermsTarget target) {
        return version(documentId, name, type, target, TermsVersionStatus.EFFECTIVE);
    }

    @Nested
    @DisplayName("목록")
    class ListTerms {

        /**
         * 조회 자체가 EFFECTIVE만 긁어 오므로, 서비스가 상태 조건을 리포지토리에 넘기는지를 본다 —
         * 여기서 조건이 빠지면 시행 예정 문서가 앱에 노출된다.
         */
        @Test
        @DisplayName("시행 중인 버전만 조회한다")
        void queriesOnlyEffectiveVersions() {
            given(termsVersionRepository.findAllByStatusWithDocument(TermsVersionStatus.EFFECTIVE))
                    .willReturn(List.of(effective(1L, "이용약관", TermsType.TERMS_OF_SERVICE, TermsTarget.USER)));

            assertThat(termsService.getTerms(null, null)).hasSize(1);
        }

        @Test
        @DisplayName("유형을 지정하면 그 유형만 남는다")
        void typeFilterNarrowsResult() {
            given(termsVersionRepository.findAllByStatusWithDocument(TermsVersionStatus.EFFECTIVE))
                    .willReturn(List.of(
                            effective(1L, "이용약관", TermsType.TERMS_OF_SERVICE, TermsTarget.USER),
                            effective(2L, "개인정보처리방침", TermsType.PRIVACY_POLICY, TermsTarget.USER)));

            List<TermsDocumentResponse> result =
                    termsService.getTerms(TermsType.PRIVACY_POLICY, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("개인정보처리방침");
        }

        /** ALL이 빠지면 모두에게 적용되는 필수 약관이 소비자 화면에서 사라진다. */
        @Test
        @DisplayName("대상을 지정하면 그 대상과 전체(ALL) 문서를 함께 내려준다")
        void targetFilterIncludesAllTarget() {
            given(termsVersionRepository.findAllByStatusWithDocument(TermsVersionStatus.EFFECTIVE))
                    .willReturn(List.of(
                            effective(1L, "소비자 이용약관", TermsType.TERMS_OF_SERVICE, TermsTarget.USER),
                            effective(2L, "공통 개인정보처리방침", TermsType.PRIVACY_POLICY, TermsTarget.ALL),
                            effective(3L, "브랜드 이용약관", TermsType.TERMS_OF_SERVICE, TermsTarget.BRAND)));

            List<TermsDocumentResponse> result = termsService.getTerms(null, TermsTarget.USER);

            assertThat(result).extracting(TermsDocumentResponse::getName)
                    .containsExactlyInAnyOrder("소비자 이용약관", "공통 개인정보처리방침");
        }

        @Test
        @DisplayName("대상을 지정하지 않으면 모든 대상이 내려간다")
        void noTargetFilterReturnsEverything() {
            given(termsVersionRepository.findAllByStatusWithDocument(TermsVersionStatus.EFFECTIVE))
                    .willReturn(List.of(
                            effective(1L, "소비자 이용약관", TermsType.TERMS_OF_SERVICE, TermsTarget.USER),
                            effective(3L, "브랜드 이용약관", TermsType.TERMS_OF_SERVICE, TermsTarget.BRAND)));

            assertThat(termsService.getTerms(null, null)).hasSize(2);
        }

        /** 목록 순서가 화면 순서다 — 유형 순서가 흔들리면 약관 화면의 항목이 매번 뒤바뀐다. */
        @Test
        @DisplayName("유형 정의 순서로 정렬되고 같은 유형 안에서는 문서 등록순이다")
        void sortedByTypeThenDocumentId() {
            TermsType first = TermsType.values()[0];
            TermsType second = TermsType.values()[1];
            given(termsVersionRepository.findAllByStatusWithDocument(TermsVersionStatus.EFFECTIVE))
                    .willReturn(List.of(
                            effective(30L, "두 번째 유형", second, TermsTarget.ALL),
                            effective(20L, "첫 유형 · 나중 등록", first, TermsTarget.ALL),
                            effective(10L, "첫 유형 · 먼저 등록", first, TermsTarget.ALL)));

            List<TermsDocumentResponse> result = termsService.getTerms(null, null);

            assertThat(result).extracting(TermsDocumentResponse::getName)
                    .containsExactly("첫 유형 · 먼저 등록", "첫 유형 · 나중 등록", "두 번째 유형");
        }

        @Test
        @DisplayName("시행 중인 문서가 없으면 빈 목록을 준다")
        void emptyWhenNothingEffective() {
            given(termsVersionRepository.findAllByStatusWithDocument(TermsVersionStatus.EFFECTIVE))
                    .willReturn(List.of());

            assertThat(termsService.getTerms(null, TermsTarget.USER)).isEmpty();
        }
    }

    @Nested
    @DisplayName("상세")
    class Detail {

        @Test
        @DisplayName("시행 중인 버전의 본문을 내려준다")
        void effectiveDocumentIsReadable() {
            given(termsVersionRepository.findAllByDocumentIdAndStatus(DOCUMENT_ID, TermsVersionStatus.EFFECTIVE))
                    .willReturn(List.of(effective(DOCUMENT_ID, "이용약관", TermsType.TERMS_OF_SERVICE, TermsTarget.USER)));

            assertThat(termsService.getTermsDetail(DOCUMENT_ID).getContent()).isEqualTo("제1조(목적) ...");
        }

        /**
         * 시행 예정만 있는 문서를 상세로 열 수 있으면 목록에서 감춘 의미가 없다 —
         * 링크를 직접 두드려도 같은 판단이어야 한다.
         */
        @Test
        @DisplayName("시행 중인 버전이 없는 문서는 404다")
        void documentWithoutEffectiveVersionIsNotFound() {
            given(termsVersionRepository.findAllByDocumentIdAndStatus(DOCUMENT_ID, TermsVersionStatus.EFFECTIVE))
                    .willReturn(List.of());

            assertThatThrownBy(() -> termsService.getTermsDetail(DOCUMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_DATA);
        }
    }
}
