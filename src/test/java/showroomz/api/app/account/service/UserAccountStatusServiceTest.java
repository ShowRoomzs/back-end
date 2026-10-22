package showroomz.api.app.account.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.account.dto.AccountStatusResponse;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.member.user.entity.UserSanction;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserSanctionRepository;
import showroomz.domain.member.user.type.UserReleaseReason;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.member.user.type.UserSuspensionReason;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 앱 계정 상태(C20 · 51-6 설계서 3절) — 배너 · 배지 · 비활성은 {@code status}, 안내 시트 · 해제 토스트는 정지 행의 두 시각으로 갈린다.
 * 상세 사유는 어떤 경로로도 내려가지 않는다(§51-6).
 */
@ExtendWith(MockitoExtension.class)
class UserAccountStatusServiceTest {

    private static final long USER_ID = 42L;
    private static final LocalDateTime SUSPENDED_AT = LocalDateTime.of(2026, 10, 10, 14, 20);

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSanctionRepository sanctionRepository;
    @Mock
    private OneToOneInquiryRepository inquiryRepository;

    @InjectMocks
    private UserAccountStatusService service;

    private Users givenUser(UserStatus status) {
        Users user = new Users();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setStatus(status);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        return user;
    }

    private static UserSanction sanction(UserSuspensionReason reason, String detail) {
        UserSanction sanction = UserSanction.suspend(USER_ID, reason, detail, 7L, "김운영", SUSPENDED_AT);
        ReflectionTestUtils.setField(sanction, "id", 3L);
        return sanction;
    }

    @Nested
    @DisplayName("상태 조회")
    class Status {

        @Test
        @DisplayName("정지 중이면 정형 사유 · 일시 · 첫 진입 안내 여부를 내리고 상세 사유는 내리지 않는다")
        void suspended() {
            givenUser(UserStatus.SUSPENDED);
            UserSanction open = sanction(UserSuspensionReason.OTHER, "상담원에게 반복적인 폭언");
            given(sanctionRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).willReturn(Optional.of(open));
            given(inquiryRepository.findFirstBySanctionIdAndStatusOrderByIdDesc(3L, InquiryStatus.WAITING))
                    .willReturn(Optional.empty());

            AccountStatusResponse response = service.getStatus(USER_ID);

            assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(response.suspension().reasonCode()).isEqualTo("OTHER");
            assertThat(response.suspension().reasonLabel()).isEqualTo("기타");
            assertThat(response.suspension().suspendedAt()).isEqualTo(SUSPENDED_AT);
            assertThat(response.suspension().noticePending()).isTrue();
            assertThat(response.suspension().pendingAppealInquiryId()).isNull();
            assertThat(response.releaseNotice()).isNull();
            assertThat(response.toString()).doesNotContain("폭언");
        }

        @Test
        @DisplayName("답변 전 정지 문의가 있으면 그 id 를 내려 「이의 신청」이 새로 쓰지 않고 그 문의로 가게 한다")
        void pendingAppeal() {
            givenUser(UserStatus.SUSPENDED);
            UserSanction open = sanction(UserSuspensionReason.ABUSIVE_LANGUAGE, null);
            open.ackNotice(SUSPENDED_AT.plusHours(1));
            given(sanctionRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).willReturn(Optional.of(open));
            OneToOneInquiry appeal = OneToOneInquiry.builder().type(CsCategory.ACCOUNT).content("이의").sanctionId(3L).build();
            ReflectionTestUtils.setField(appeal, "id", 77L);
            given(inquiryRepository.findFirstBySanctionIdAndStatusOrderByIdDesc(3L, InquiryStatus.WAITING))
                    .willReturn(Optional.of(appeal));

            AccountStatusResponse response = service.getStatus(USER_ID);

            assertThat(response.suspension().pendingAppealInquiryId()).isEqualTo(77L);
            assertThat(response.suspension().noticePending()).isFalse();
        }

        @Test
        @DisplayName("정지 행 없이 정지된 옛 회원 — 사유 · 일시를 지어내지 않고, 확인을 기록할 행이 없으니 시트도 띄우지 않는다")
        void legacySuspension() {
            givenUser(UserStatus.SUSPENDED);
            given(sanctionRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).willReturn(Optional.empty());

            AccountStatusResponse response = service.getStatus(USER_ID);

            assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(response.suspension().reasonCode()).isNull();
            assertThat(response.suspension().reasonLabel()).isNull();
            assertThat(response.suspension().suspendedAt()).isNull();
            assertThat(response.suspension().noticePending()).isFalse();
            verifyNoInteractions(inquiryRepository);
        }

        @Test
        @DisplayName("가장 최근 정지가 해제되고 토스트를 아직 확인하지 않았으면 해제 토스트를 내린다")
        void releaseNoticePending() {
            givenUser(UserStatus.NORMAL);
            UserSanction latest = sanction(UserSuspensionReason.RETURN_ABUSE, null);
            latest.release(UserReleaseReason.RESOLVED, 7L, "김운영", SUSPENDED_AT.plusDays(2));
            given(sanctionRepository.findFirstByUserIdOrderBySuspendedAtDescIdDesc(USER_ID)).willReturn(Optional.of(latest));

            AccountStatusResponse response = service.getStatus(USER_ID);

            assertThat(response.status()).isEqualTo(UserStatus.NORMAL);
            assertThat(response.suspension()).isNull();
            assertThat(response.releaseNotice().releasedAt()).isEqualTo(SUSPENDED_AT.plusDays(2));
        }

        @Test
        @DisplayName("토스트를 확인했거나 정지된 적이 없으면 해제 토스트는 없다")
        void noReleaseNotice() {
            givenUser(UserStatus.NORMAL);
            UserSanction latest = sanction(UserSuspensionReason.RETURN_ABUSE, null);
            latest.release(UserReleaseReason.RESOLVED, 7L, "김운영", SUSPENDED_AT.plusDays(2));
            latest.ackReleaseNotice(SUSPENDED_AT.plusDays(3));
            given(sanctionRepository.findFirstByUserIdOrderBySuspendedAtDescIdDesc(USER_ID))
                    .willReturn(Optional.of(latest))
                    .willReturn(Optional.empty());

            assertThat(service.getStatus(USER_ID).releaseNotice()).isNull();
            assertThat(service.getStatus(USER_ID).releaseNotice()).isNull();
        }

        @Test
        @DisplayName("탈퇴 회원은 403 · 인증 정보가 없으면 401 계열")
        void rejectsWithdrawnAndAnonymous() {
            givenUser(UserStatus.WITHDRAWN);

            assertThatThrownBy(() -> service.getStatus(USER_ID))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_WITHDRAWN);
            assertThatThrownBy(() -> service.getStatus(null))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    @Nested
    @DisplayName("1회 노출 확인")
    class Ack {

        @Test
        @DisplayName("안내 시트 확인은 열린 정지에 시각을 남긴다 — 그 사이 해제됐으면 할 일이 없다")
        void ackSuspensionNotice() {
            givenUser(UserStatus.SUSPENDED);
            UserSanction open = sanction(UserSuspensionReason.ABUSIVE_LANGUAGE, null);
            given(sanctionRepository.findByUserIdAndReleasedAtIsNull(USER_ID))
                    .willReturn(Optional.of(open))
                    .willReturn(Optional.empty());

            service.ackSuspensionNotice(USER_ID);
            service.ackSuspensionNotice(USER_ID);

            assertThat(open.isNoticePending()).isFalse();
        }

        @Test
        @DisplayName("해제 토스트 확인은 확인하지 않은 해제를 한 번에 모두 닫는다")
        void ackReleaseNoticeClosesAll() {
            givenUser(UserStatus.NORMAL);
            UserSanction first = sanction(UserSuspensionReason.RETURN_ABUSE, null);
            first.release(UserReleaseReason.MISTAKE, 7L, "김운영", SUSPENDED_AT.plusDays(1));
            UserSanction second = sanction(UserSuspensionReason.MULTIPLE_REPORTS, null);
            second.release(UserReleaseReason.RESOLVED, 7L, "김운영", SUSPENDED_AT.plusDays(5));
            given(sanctionRepository.findByUserIdAndReleasedAtIsNotNullAndReleaseNoticeAckedAtIsNull(USER_ID))
                    .willReturn(List.of(first, second));

            service.ackReleaseNotice(USER_ID);

            assertThat(first.isReleaseNoticePending()).isFalse();
            assertThat(second.isReleaseNoticePending()).isFalse();
        }
    }
}
