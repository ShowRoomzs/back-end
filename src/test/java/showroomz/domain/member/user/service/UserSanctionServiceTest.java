package showroomz.domain.member.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.history.entity.UserStatusHistory;
import showroomz.domain.history.repository.UserStatusHistoryRepository;
import showroomz.domain.member.user.entity.UserSanction;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserSanctionRepository;
import showroomz.domain.member.user.type.UserAdminEventType;
import showroomz.domain.member.user.type.UserReleaseReason;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.member.user.type.UserSuspensionReason;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 소비자 정지 · 해제 · 재가입 복구(51-6 설계서 2 · 6절) — 규칙의 정본이 이 서비스다. HTTP 경로는 {@code ConsumerSanctionIntegrationTest}가 보고,
 * 여기서는 판정 순서와 「한 트랜잭션에서 함께 바뀌는 것」(정지 행 · 회원 상태 · 전이 로그 · 처리 이력 · 알림)을 못 박는다.
 *
 * <p>트랜잭션 밖에서 부르므로 {@code AfterCommit}이 알림을 바로 실행한다 — 알림 호출 여부를 그대로 확인할 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class UserSanctionServiceTest {

    private static final long USER_ID = 42L;
    private static final long OPERATOR_ID = 7L;
    private static final String OPERATOR = "김운영";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 14, 20);

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSanctionRepository sanctionRepository;
    @Mock
    private UserStatusHistoryRepository statusHistoryRepository;
    @Mock
    private UserAdminHistoryRecorder historyRecorder;
    @Mock
    private UserSanctionNotifier notifier;

    @InjectMocks
    private UserSanctionService service;

    private Users givenLocked(RoleType roleType, UserStatus status) {
        LocalDateTime created = NOW.minusMonths(3);
        Users user = new Users("mia", "미아", "mia@showroomz.test", "Y", null, ProviderType.KAKAO, roleType, created, created);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.updateStatus(status);
        given(userRepository.findForUpdate(USER_ID)).willReturn(Optional.of(user));
        return user;
    }

    private UserStatusHistory savedTransition() {
        ArgumentCaptor<UserStatusHistory> captor = ArgumentCaptor.forClass(UserStatusHistory.class);
        verify(statusHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("정지")
    class Suspend {

        @Test
        @DisplayName("정지 행 · 회원 상태 · 전이 로그 · 처리 이력을 함께 바꾸고 앱 문구로 알림을 보낸다")
        void suspendsTogether() {
            Users user = givenLocked(RoleType.USER, UserStatus.NORMAL);
            given(sanctionRepository.save(any(UserSanction.class))).willAnswer(returnsFirstArg());

            UserSanction sanction = service.suspend(USER_ID, UserSuspensionReason.OTHER, "  상담원 폭언  ", OPERATOR_ID,
                    OPERATOR, NOW);

            assertThat(sanction.getReasonDetail()).isEqualTo("상담원 폭언");
            assertThat(sanction.getSuspendedBy()).isEqualTo(OPERATOR_ID);
            assertThat(sanction.isNoticePending()).isTrue();
            assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);

            UserStatusHistory transition = savedTransition();
            assertThat(transition.getPreviousStatus()).isEqualTo(UserStatus.NORMAL);
            assertThat(transition.getNewStatus()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(transition.getReason()).isEqualTo("OTHER");

            verify(historyRecorder).byOperator(eq(USER_ID), eq(UserAdminEventType.SUSPENDED), eq(OPERATOR_ID),
                    eq(OPERATOR), eq("기타(직접 입력) — 상담원 폭언"), eq(UserAdminHistoryRecorder.REF_SANCTION), any(),
                    eq(NOW));
            verify(notifier).suspended(USER_ID, "기타");
        }

        @Test
        @DisplayName("사유가 없거나 기타인데 상세가 비면 회원을 잠그기도 전에 400 이다")
        void validatesBeforeLocking() {
            assertThatThrownBy(() -> service.suspend(USER_ID, null, null, OPERATOR_ID, OPERATOR, NOW))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> service.suspend(USER_ID, UserSuspensionReason.OTHER, " ", OPERATOR_ID, OPERATOR, NOW))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_SUSPEND_DETAIL_REQUIRED);

            verifyNoInteractions(userRepository, sanctionRepository, notifier);
        }

        @Test
        @DisplayName("인플루언서 · 가입 미완료 계정은 소비자 정지 대상이 아니다 — 존재를 알리지 않는 404")
        void nonConsumerIsNotFound() {
            givenLocked(RoleType.CREATOR, UserStatus.NORMAL);

            assertThatThrownBy(() -> service.suspend(USER_ID, UserSuspensionReason.RETURN_ABUSE, null, OPERATOR_ID,
                    OPERATOR, NOW))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
            verify(sanctionRepository, never()).save(any());
        }

        @Test
        @DisplayName("탈퇴 회원은 409 USER_SANCTION_WITHDRAWN")
        void withdrawnIsRejected() {
            givenLocked(RoleType.USER, UserStatus.WITHDRAWN);

            assertThatThrownBy(() -> service.suspend(USER_ID, UserSuspensionReason.RETURN_ABUSE, null, OPERATOR_ID,
                    OPERATOR, NOW))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_SANCTION_WITHDRAWN);
        }

        @Test
        @DisplayName("상태가 정상이어도 열린 정지 행이 남아 있으면 이중 정지로 본다")
        void openRowMeansAlreadySuspended() {
            givenLocked(RoleType.USER, UserStatus.NORMAL);
            given(sanctionRepository.existsByUserIdAndReleasedAtIsNull(USER_ID)).willReturn(true);

            assertThatThrownBy(() -> service.suspend(USER_ID, UserSuspensionReason.RETURN_ABUSE, null, OPERATOR_ID,
                    OPERATOR, NOW))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_ALREADY_SUSPENDED);
            verify(sanctionRepository, never()).save(any());
            verifyNoInteractions(notifier);
        }
    }

    @Nested
    @DisplayName("해제")
    class Release {

        @Test
        @DisplayName("열린 정지 행을 닫고 정상으로 되돌리며 해제 알림을 보낸다")
        void releasesOpenSanction() {
            Users user = givenLocked(RoleType.USER, UserStatus.SUSPENDED);
            UserSanction open = UserSanction.suspend(USER_ID, UserSuspensionReason.ABUSIVE_LANGUAGE, null, OPERATOR_ID,
                    OPERATOR, NOW.minusDays(3));
            given(sanctionRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).willReturn(Optional.of(open));

            UserSanction released = service.release(USER_ID, UserReleaseReason.TERM_ENDED, 8L, "박운영", NOW);

            assertThat(released).isSameAs(open);
            assertThat(open.getReleasedAt()).isEqualTo(NOW);
            assertThat(open.getReleaseReason()).isEqualTo(UserReleaseReason.TERM_ENDED);
            assertThat(open.isReleaseNoticePending()).isTrue();
            assertThat(user.getStatus()).isEqualTo(UserStatus.NORMAL);
            assertThat(savedTransition().getReason()).isEqualTo("TERM_ENDED");
            verify(historyRecorder).byOperator(eq(USER_ID), eq(UserAdminEventType.SUSPENSION_RELEASED), eq(8L),
                    eq("박운영"), eq("제재 기간 종료"), eq(UserAdminHistoryRecorder.REF_SANCTION), any(), eq(NOW));
            verify(notifier).released(USER_ID);
        }

        @Test
        @DisplayName("정지 행 없이 정지된 옛 회원도 상태만 되돌린다 — 영원히 풀리지 않는 회원을 만들지 않는다")
        void legacySuspensionWithoutRow() {
            Users user = givenLocked(RoleType.USER, UserStatus.SUSPENDED);
            given(sanctionRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).willReturn(Optional.empty());

            UserSanction released = service.release(USER_ID, UserReleaseReason.MISTAKE, OPERATOR_ID, OPERATOR, NOW);

            assertThat(released).isNull();
            assertThat(user.getStatus()).isEqualTo(UserStatus.NORMAL);
            verify(historyRecorder).byOperator(eq(USER_ID), eq(UserAdminEventType.SUSPENSION_RELEASED), eq(OPERATOR_ID),
                    eq(OPERATOR), eq("오처리 정정"), eq(UserAdminHistoryRecorder.REF_SANCTION), eq(null), eq(NOW));
        }

        @Test
        @DisplayName("사유가 없으면 400 · 정지가 아니면 409 · 탈퇴면 409")
        void rejectsInvalidRelease() {
            assertThatThrownBy(() -> service.release(USER_ID, null, OPERATOR_ID, OPERATOR, NOW))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_RELEASE_REASON_REQUIRED);
            verifyNoInteractions(userRepository);

            givenLocked(RoleType.USER, UserStatus.NORMAL);
            assertThatThrownBy(() -> service.release(USER_ID, UserReleaseReason.RESOLVED, OPERATOR_ID, OPERATOR, NOW))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_SUSPENDED);

            givenLocked(RoleType.USER, UserStatus.WITHDRAWN);
            assertThatThrownBy(() -> service.release(USER_ID, UserReleaseReason.RESOLVED, OPERATOR_ID, OPERATOR, NOW))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_SANCTION_WITHDRAWN);
            verifyNoInteractions(notifier);
        }
    }

    @Nested
    @DisplayName("재가입 복구")
    class Rejoin {

        private Users withdrawn() {
            Users user = new Users();
            ReflectionTestUtils.setField(user, "id", USER_ID);
            user.updateStatus(UserStatus.WITHDRAWN);
            return user;
        }

        @Test
        @DisplayName("열린 정지가 있으면 정지로 복귀하고 안내 시트를 다시 띄운다 — 탈퇴 · 재가입으로 정지가 풀리지 않는다")
        void restoresSuspension() {
            Users user = withdrawn();
            UserSanction open = UserSanction.suspend(USER_ID, UserSuspensionReason.PAYMENT_FRAUD_SUSPECTED, null,
                    OPERATOR_ID, OPERATOR, NOW.minusDays(10));
            open.ackNotice(NOW.minusDays(9));
            given(sanctionRepository.findByUserIdAndReleasedAtIsNull(USER_ID)).willReturn(Optional.of(open));

            UserStatus restored = service.restoreOnRejoin(user);

            assertThat(restored).isEqualTo(UserStatus.SUSPENDED);
            assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(open.isNoticePending()).isTrue();
            UserStatusHistory transition = savedTransition();
            assertThat(transition.getPreviousStatus()).isEqualTo(UserStatus.WITHDRAWN);
            assertThat(transition.getNewStatus()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(transition.getReason()).isEqualTo(UserSanctionService.REJOIN_REASON);
            verifyNoInteractions(notifier, historyRecorder);
        }

        @Test
        @DisplayName("열린 정지가 없으면 정상으로 돌아온다")
        void restoresNormal() {
            Users user = withdrawn();
            given(sanctionRepository.findByUserIdAndReleasedAtIsNull(anyLong())).willReturn(Optional.empty());

            assertThat(service.restoreOnRejoin(user)).isEqualTo(UserStatus.NORMAL);
            assertThat(user.getStatus()).isEqualTo(UserStatus.NORMAL);
            assertThat(savedTransition().getNewStatus()).isEqualTo(UserStatus.NORMAL);
        }
    }
}
