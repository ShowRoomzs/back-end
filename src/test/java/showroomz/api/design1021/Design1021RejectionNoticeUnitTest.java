package showroomz.api.design1021;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import showroomz.api.admin.creator.type.CreatorRejectionReasonType;
import showroomz.api.creator.auth.service.CreatorRejectionNotice;
import showroomz.domain.member.creator.entity.CreatorApplication;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1021 설계서 9절 · 기-36 — 반려 통지 하나가 어드민 미리보기 · 실제 메일 · 스튜디오 반려 모달의 문안이다. 사유마다 통지 라벨이 정해져 있고
 * 내부 라벨 · 상세 사유는 통지에 나가지 않는다.
 */
@DisplayName("1021 기-36 반려 통지 — 사유 4종 통지 라벨 · 내부 라벨 미노출 · 반려일 · 재신청일 · HTML 이스케이프")
class Design1021RejectionNoticeUnitTest {

    private static final LocalDate REJECTED_ON = LocalDate.of(2026, 10, 23);

    @ParameterizedTest(name = "{0} → 통지 「{1}」")
    @CsvSource({
            "CHANNEL_PERFORMANCE_UNVERIFIABLE, 채널 실적 확인 불가",
            "IDENTITY_INFO_MISMATCH, 본인 인증 정보 불일치",
            "SUSPECTED_FAKE_FOLLOWERS, 채널 운영 기준 미충족",
            "OTHER, 기타"
    })
    @DisplayName("사유별 통지 라벨 — 본문 「반려 사유」 줄이 통지 라벨이다")
    void noticeLabelPerReason(CreatorRejectionReasonType type, String noticeLabel) {
        CreatorRejectionNotice notice = CreatorRejectionNotice.of(type, REJECTED_ON);

        assertThat(notice.noticeLabel()).isEqualTo(noticeLabel).isEqualTo(type.getNoticeLabel());
        assertThat(notice.body()).contains("반려 사유: " + noticeLabel);
        assertThat(notice.subject()).isEqualTo(CreatorRejectionNotice.SUBJECT);
    }

    @Test
    @DisplayName("허위 팔로워 의심 — 내부 라벨은 통지 어디에도 없다(본문 · HTML)")
    void internalLabelNeverLeaks() {
        CreatorRejectionNotice notice = CreatorRejectionNotice.of(CreatorRejectionReasonType.SUSPECTED_FAKE_FOLLOWERS,
                REJECTED_ON);

        assertThat(CreatorRejectionReasonType.SUSPECTED_FAKE_FOLLOWERS.getDescription()).isEqualTo("허위 팔로워 의심");
        assertThat(notice.body()).doesNotContain("허위 팔로워");
        assertThat(notice.htmlBody()).doesNotContain("허위 팔로워");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(CreatorRejectionReasonType.class)
    @DisplayName("날짜 — 반려일 · 재신청 가능일(반려일 + 재신청 대기 일수)을 yyyy.MM.dd 로 쓴다")
    void datesUseCooldown(CreatorRejectionReasonType type) {
        CreatorRejectionNotice notice = CreatorRejectionNotice.of(type, REJECTED_ON);

        assertThat(CreatorApplication.REAPPLY_COOLDOWN_DAYS).isEqualTo(14);
        assertThat(notice.rejectedOn()).isEqualTo(REJECTED_ON);
        assertThat(notice.reapplyAvailableOn()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(notice.body()).contains("반려일: 2026.10.23").contains("2026.11.06 부터 새로 신청할 수 있습니다.");
    }

    @Test
    @DisplayName("월말 반려 — 재신청일은 달을 넘긴다")
    void cooldownCrossesMonth() {
        CreatorRejectionNotice notice = CreatorRejectionNotice.of(CreatorRejectionReasonType.OTHER, LocalDate.of(2026, 12, 25));

        assertThat(notice.reapplyAvailableOn()).isEqualTo(LocalDate.of(2027, 1, 8));
        assertThat(notice.body()).contains("2027.01.08 부터");
    }

    @Test
    @DisplayName("HTML 본문 — 줄마다 단락 · 본문 줄 수와 단락 수가 같다 · 특수문자는 이스케이프")
    void htmlBodyParagraphs() {
        CreatorRejectionNotice notice = CreatorRejectionNotice.of(CreatorRejectionReasonType.IDENTITY_INFO_MISMATCH,
                REJECTED_ON);
        String html = notice.htmlBody();

        assertThat(html).startsWith("<html>").endsWith("</body></html>");
        assertThat(html.split("<p>", -1).length - 1).isEqualTo(notice.body().split("\n").length);
        assertThat(html).contains("<p>반려 사유: 본인 인증 정보 불일치</p>");

        CreatorRejectionNotice tampered = new CreatorRejectionNotice(notice.reasonType(), notice.noticeLabel(),
                notice.rejectedOn(), notice.reapplyAvailableOn(), notice.subject(), "<script>alert(1)</script>");
        assertThat(tampered.htmlBody()).contains("&lt;script&gt;").doesNotContain("<script>");
    }

    @Test
    @DisplayName("코드 해석 — 4종만 받는다 · 모르는 코드 · null 은 빈 값")
    void fromCode() {
        assertThat(CreatorRejectionReasonType.fromCode("SUSPECTED_FAKE_FOLLOWERS"))
                .contains(CreatorRejectionReasonType.SUSPECTED_FAKE_FOLLOWERS);
        assertThat(CreatorRejectionReasonType.fromCode("FAKE_FOLLOWERS")).isEmpty();
        assertThat(CreatorRejectionReasonType.fromCode(null)).isEmpty();
        assertThat(CreatorRejectionReasonType.values()).hasSize(4);
    }
}
