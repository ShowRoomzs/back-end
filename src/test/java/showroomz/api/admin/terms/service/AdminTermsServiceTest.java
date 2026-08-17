package showroomz.api.admin.terms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.admin.terms.dto.AdminTermsDocumentRegisterRequest;
import showroomz.api.admin.terms.dto.AdminTermsVersionRegisterRequest;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.terms.entity.TermsDocument;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.repository.TermsDocumentRepository;
import showroomz.domain.terms.repository.TermsVersionRepository;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;
import showroomz.domain.terms.type.TermsVersionStatus;
import showroomz.global.error.exception.BusinessException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminTermsServiceTest {

    private static final long DOCUMENT_ID = 7L;
    private static final long OPERATOR_ID = 1L;
    private static final LocalDate TOMORROW = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);

    @Mock
    private TermsDocumentRepository termsDocumentRepository;
    @Mock
    private TermsVersionRepository termsVersionRepository;
    @Mock
    private SellerRepository sellerRepository;

    @InjectMocks
    private AdminTermsService adminTermsService;

    @Test
    @DisplayName("새 버전은 시행 예정 상태로 등록된다 — 시행일 00:00에 배치가 교체한다")
    void registersVersionAsScheduled() {
        TermsDocument document = document(false);
        givenDocument(document);
        given(termsVersionRepository.findAllByDocumentId(DOCUMENT_ID))
                .willReturn(List.of(version(document, "3.1", LocalDate.now().minusMonths(2))));
        given(termsVersionRepository.existsByDocumentIdAndVersionNumber(DOCUMENT_ID, "3.2")).willReturn(false);
        given(termsVersionRepository.save(any(TermsVersion.class))).willAnswer(invocation -> {
            TermsVersion saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });

        Long versionId = adminTermsService.registerVersion(
                DOCUMENT_ID, versionRequest("3.2", TOMORROW), OPERATOR_ID);

        ArgumentCaptor<TermsVersion> captor = ArgumentCaptor.forClass(TermsVersion.class);
        verify(termsVersionRepository).save(captor.capture());
        assertThat(versionId).isEqualTo(99L);
        assertThat(captor.getValue().getStatus()).isEqualTo(TermsVersionStatus.SCHEDULED);
        assertThat(captor.getValue().getVersionNumber()).isEqualTo("3.2");
        assertThat(captor.getValue().getDisplayVersion()).isEqualTo("v3.2");
    }

    @Test
    @DisplayName("접두 v가 붙은 버전 번호는 거부한다 — v는 필드 밖 표기라 값에 섞이면 표기가 갈린다")
    void rejectsVersionNumberWithPrefix() {
        givenDocument(document(false));

        assertThatThrownBy(() -> adminTermsService.registerVersion(
                DOCUMENT_ID, versionRequest("v3.2", TOMORROW), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("접두 v");

        verify(termsVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("숫자와 점 외의 문자가 섞인 버전 번호는 거부한다")
    void rejectsMalformedVersionNumber() {
        givenDocument(document(false));

        assertThatThrownBy(() -> adminTermsService.registerVersion(
                DOCUMENT_ID, versionRequest("3.2 beta", TOMORROW), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("숫자와 점");
    }

    @Test
    @DisplayName("기존 버전보다 낮거나 같은 번호는 거부한다 — 역행 번호는 서버에서 막는다")
    void rejectsBackwardVersionNumber() {
        TermsDocument document = document(false);
        givenDocument(document);
        given(termsVersionRepository.findAllByDocumentId(DOCUMENT_ID))
                .willReturn(List.of(version(document, "3.10", LocalDate.now().minusMonths(2))));
        given(termsVersionRepository.existsByDocumentIdAndVersionNumber(DOCUMENT_ID, "3.9")).willReturn(false);

        assertThatThrownBy(() -> adminTermsService.registerVersion(
                DOCUMENT_ID, versionRequest("3.9", TOMORROW), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("높은 번호");
    }

    @Test
    @DisplayName("이미 등록된 버전 번호는 거부한다")
    void rejectsDuplicateVersionNumber() {
        TermsDocument document = document(false);
        givenDocument(document);
        given(termsVersionRepository.findAllByDocumentId(DOCUMENT_ID))
                .willReturn(List.of(version(document, "3.1", LocalDate.now().minusMonths(2))));
        given(termsVersionRepository.existsByDocumentIdAndVersionNumber(DOCUMENT_ID, "3.1")).willReturn(true);

        assertThatThrownBy(() -> adminTermsService.registerVersion(
                DOCUMENT_ID, versionRequest("3.1", TOMORROW), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 등록된 버전");
    }

    @Test
    @DisplayName("오늘 이전 시행일은 거부한다 — 이미 시행됐어야 할 문서를 뒤늦게 등록하는 셈이다")
    void rejectsPastEffectiveDate() {
        givenDocument(document(false));

        assertThatThrownBy(() -> adminTermsService.registerVersion(
                DOCUMENT_ID, versionRequest("3.2", LocalDate.now(ZoneId.of("Asia/Seoul"))), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("오늘 이후");
    }

    @Test
    @DisplayName("기존 버전의 시행일보다 앞선 시행일은 거부한다 — 같은 날 두 버전이 시행될 수 없다")
    void rejectsEffectiveDateBeforeExistingVersion() {
        TermsDocument document = document(false);
        givenDocument(document);
        given(termsVersionRepository.findAllByDocumentId(DOCUMENT_ID))
                .willReturn(List.of(version(document, "3.1", TOMORROW.plusMonths(1))));
        given(termsVersionRepository.existsByDocumentIdAndVersionNumber(DOCUMENT_ID, "3.2")).willReturn(false);

        assertThatThrownBy(() -> adminTermsService.registerVersion(
                DOCUMENT_ID, versionRequest("3.2", TOMORROW), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("시행일");
    }

    @Test
    @DisplayName("구버전 문서에는 새 버전을 등록할 수 없다")
    void rejectsVersionOnSupersededDocument() {
        givenDocument(document(true));

        assertThatThrownBy(() -> adminTermsService.registerVersion(
                DOCUMENT_ID, versionRequest("3.2", TOMORROW), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("구버전 문서");
    }

    @Test
    @DisplayName("신규 문서의 첫 버전은 v1.0으로 자동 부여된다 — 버전 번호를 입력받지 않는다")
    void registersFirstVersionAsOnePointZero() {
        given(termsDocumentRepository.existsByNameAndTarget("소비자 이용약관", TermsTarget.USER)).willReturn(false);
        given(termsDocumentRepository.save(any(TermsDocument.class))).willAnswer(invocation -> {
            TermsDocument saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", DOCUMENT_ID);
            return saved;
        });

        Long documentId = adminTermsService.registerDocument(documentRequest(), OPERATOR_ID);

        ArgumentCaptor<TermsVersion> captor = ArgumentCaptor.forClass(TermsVersion.class);
        verify(termsVersionRepository).save(captor.capture());
        assertThat(documentId).isEqualTo(DOCUMENT_ID);
        assertThat(captor.getValue().getVersionNumber()).isEqualTo("1.0");
        assertThat(captor.getValue().getStatus()).isEqualTo(TermsVersionStatus.SCHEDULED);
    }

    @Test
    @DisplayName("문서명은 대상까지 같아야 중복이다 — 마케팅 동의는 대상별로 같은 이름을 쓴다")
    void rejectsDuplicateNameWithinSameTarget() {
        given(termsDocumentRepository.existsByNameAndTarget("소비자 이용약관", TermsTarget.USER)).willReturn(true);

        assertThatThrownBy(() -> adminTermsService.registerDocument(documentRequest(), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 등록된 문서명");

        verify(termsDocumentRepository, never()).save(any());
    }

    private void givenDocument(TermsDocument document) {
        given(termsDocumentRepository.findById(DOCUMENT_ID)).willReturn(Optional.of(document));
    }

    private static TermsDocument document(boolean superseded) {
        TermsDocument document = TermsDocument.builder()
                .name("소비자 이용약관")
                .type(TermsType.TERMS_OF_SERVICE)
                .target(TermsTarget.USER)
                .registeredBy(OPERATOR_ID)
                .build();
        ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
        ReflectionTestUtils.setField(document, "superseded", superseded);
        return document;
    }

    private static TermsVersion version(TermsDocument document, String versionNumber, LocalDate effectiveDate) {
        return TermsVersion.builder()
                .document(document)
                .versionNumber(versionNumber)
                .effectiveDate(effectiveDate)
                .content("제1조(목적) ...")
                .registeredBy(OPERATOR_ID)
                .build();
    }

    private static AdminTermsVersionRegisterRequest versionRequest(String versionNumber, LocalDate effectiveDate) {
        AdminTermsVersionRegisterRequest request = new AdminTermsVersionRegisterRequest();
        ReflectionTestUtils.setField(request, "versionNumber", versionNumber);
        ReflectionTestUtils.setField(request, "effectiveDate", effectiveDate);
        ReflectionTestUtils.setField(request, "content", "제1조(목적) ...");
        return request;
    }

    private static AdminTermsDocumentRegisterRequest documentRequest() {
        AdminTermsDocumentRegisterRequest request = new AdminTermsDocumentRegisterRequest();
        ReflectionTestUtils.setField(request, "name", "소비자 이용약관");
        ReflectionTestUtils.setField(request, "type", TermsType.TERMS_OF_SERVICE);
        ReflectionTestUtils.setField(request, "target", TermsTarget.USER);
        ReflectionTestUtils.setField(request, "effectiveDate", TOMORROW);
        ReflectionTestUtils.setField(request, "content", "제1조(목적) ...");
        return request;
    }
}
