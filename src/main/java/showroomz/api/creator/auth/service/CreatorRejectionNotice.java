package showroomz.api.creator.auth.service;

import org.springframework.web.util.HtmlUtils;
import showroomz.api.admin.creator.type.CreatorRejectionReasonType;
import showroomz.domain.member.creator.entity.CreatorApplication;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 인플루언서 입점 반려 통지 — 1021 결정 노트 기-36 · 설계서 9절.
 *
 * <p>정형 사유(통지 라벨) · 반려일 · 재신청 가능일 · 재신청 방법만 담는다. 상세 사유는 담지 않는다. 어드민 C1 「신청자 통지 미리보기」 ·
 * 실제 발송 메일 · 스튜디오 로그인 반려 모달(C)이 이 하나를 쓴다 — 세 곳의 문안 · 날짜 형식이 어긋나지 않게.
 */
public record CreatorRejectionNotice(
        CreatorRejectionReasonType reasonType,
        String noticeLabel,
        LocalDate rejectedOn,
        LocalDate reapplyAvailableOn,
        String subject,
        String body) {

    public static final String SUBJECT = "[SHOWROOMZ] 쇼룸 스튜디오 신청 결과 안내";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public static CreatorRejectionNotice of(CreatorRejectionReasonType reasonType, LocalDate rejectedOn) {
        LocalDate reapplyOn = rejectedOn.plusDays(CreatorApplication.REAPPLY_COOLDOWN_DAYS);
        String label = reasonType.getNoticeLabel();
        String body = String.join("\n",
                "SHOWROOMZ 인플루언서 입점 신청이 반려되었습니다.",
                "반려 사유: " + label,
                "반려일: " + DATE.format(rejectedOn),
                DATE.format(reapplyOn) + " 부터 새로 신청할 수 있습니다.",
                "재신청 가능일 이후 쇼룸 스튜디오에 로그인하면 새 신청서를 작성할 수 있습니다.",
                "이 메일은 발신 전용입니다.");
        return new CreatorRejectionNotice(reasonType, label, rejectedOn, reapplyOn, SUBJECT, body);
    }

    /** 메일 본문(HTML) — 줄마다 단락. */
    public String htmlBody() {
        StringBuilder html = new StringBuilder("<html><body style=\"font-family: sans-serif; line-height: 1.6; color: #222;\">");
        for (String line : body.split("\n")) {
            html.append("<p>").append(HtmlUtils.htmlEscape(line)).append("</p>");
        }
        return html.append("</body></html>").toString();
    }
}
