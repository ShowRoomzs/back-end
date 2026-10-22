package showroomz.domain.member.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.member.user.type.UserReleaseReason;
import showroomz.domain.member.user.type.UserSuspensionReason;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소비자 정지 행(51-6 설계서 1-2) — 「1회 노출」 두 가지(안내 시트 · 해제 토스트)는 이 행의 두 시각으로만 판정한다(0-7). 판정이 틀리면 앱이 시트를
 * 매번 띄우거나 한 번도 띄우지 않는다.
 */
class UserSanctionTest {

    private static final LocalDateTime SUSPENDED_AT = LocalDateTime.of(2026, 10, 10, 14, 20);

    private static UserSanction sanction(UserSuspensionReason reason, String detail) {
        return UserSanction.suspend(1L, reason, detail, 7L, "김운영", SUSPENDED_AT);
    }

    @Test
    @DisplayName("막 정지된 행은 열려 있고 안내 시트를 기다린다 — 해제 토스트는 아직 아니다")
    void freshSanctionIsOpenAndWaitsForNotice() {
        UserSanction sanction = sanction(UserSuspensionReason.ABUSIVE_LANGUAGE, null);

        assertThat(sanction.isOpen()).isTrue();
        assertThat(sanction.isNoticePending()).isTrue();
        assertThat(sanction.isReleaseNoticePending()).isFalse();
        assertThat(sanction.getSuspendedByName()).isEqualTo("김운영");
    }

    @Test
    @DisplayName("안내 시트 확인은 처음 시각만 남긴다 — 두 번 눌러도 덮어쓰지 않는다")
    void noticeAckKeepsFirstTime() {
        UserSanction sanction = sanction(UserSuspensionReason.ABUSIVE_LANGUAGE, null);
        LocalDateTime first = SUSPENDED_AT.plusHours(1);

        sanction.ackNotice(first);
        sanction.ackNotice(first.plusHours(5));

        assertThat(sanction.getNoticeAckedAt()).isEqualTo(first);
        assertThat(sanction.isNoticePending()).isFalse();
    }

    @Test
    @DisplayName("재가입으로 되살아난 정지는 안내 시트를 다시 띄운다")
    void resetNoticeShowsSheetAgain() {
        UserSanction sanction = sanction(UserSuspensionReason.PAYMENT_FRAUD_SUSPECTED, null);
        sanction.ackNotice(SUSPENDED_AT.plusHours(1));

        sanction.resetNotice();

        assertThat(sanction.isNoticePending()).isTrue();
    }

    @Test
    @DisplayName("해제되면 안내 시트는 끝나고 해제 토스트를 한 번 기다린다")
    void releaseSwitchesToReleaseNotice() {
        UserSanction sanction = sanction(UserSuspensionReason.RETURN_ABUSE, null);
        LocalDateTime releasedAt = SUSPENDED_AT.plusDays(3);

        sanction.release(UserReleaseReason.RESOLVED, 8L, "박운영", releasedAt);

        assertThat(sanction.isOpen()).isFalse();
        assertThat(sanction.isNoticePending()).isFalse();
        assertThat(sanction.isReleaseNoticePending()).isTrue();
        assertThat(sanction.getReleaseReason()).isEqualTo(UserReleaseReason.RESOLVED);
        assertThat(sanction.getReleasedByName()).isEqualTo("박운영");

        sanction.ackReleaseNotice(releasedAt.plusHours(1));
        sanction.ackReleaseNotice(releasedAt.plusHours(9));

        assertThat(sanction.isReleaseNoticePending()).isFalse();
        assertThat(sanction.getReleaseNoticeAckedAt()).isEqualTo(releasedAt.plusHours(1));
    }

    @Test
    @DisplayName("이력 문장은 사유 라벨 + 상세 — 상세가 비면 라벨만")
    void reasonText() {
        assertThat(sanction(UserSuspensionReason.MULTIPLE_REPORTS, null).reasonText()).isEqualTo("신고 다수 접수");
        assertThat(sanction(UserSuspensionReason.MULTIPLE_REPORTS, "  ").reasonText()).isEqualTo("신고 다수 접수");
        assertThat(sanction(UserSuspensionReason.OTHER, "상담원 폭언").reasonText()).isEqualTo("기타(직접 입력) — 상담원 폭언");
    }

    @Test
    @DisplayName("사유 enum — 기타만 상세가 필수이고, 앱 문구는 기타의 입력 안내를 뺀다")
    void suspensionReasonRules() {
        for (UserSuspensionReason reason : UserSuspensionReason.values()) {
            assertThat(reason.isDetailRequired()).isEqualTo(reason == UserSuspensionReason.OTHER);
            if (reason != UserSuspensionReason.OTHER) {
                assertThat(reason.getAppLabel()).isEqualTo(reason.getLabel());
            }
        }
        assertThat(UserSuspensionReason.OTHER.getAppLabel()).isEqualTo("기타");
    }
}
