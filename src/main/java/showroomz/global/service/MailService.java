package showroomz.global.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final String SELLER_ADMIN_LOGIN_URL = "https://showroomz.co.kr/seller/login";
    private static final String SELLER_REAPPLY_URL = "https://showroomz.co.kr/seller/signup";
    private static final String CREATOR_LOGIN_URL = "https://showroomz.co.kr/creator/login";
    private static final String OPS_TEAM = "SHOWROOMZ 운영팀";
    private static final DateTimeFormatter SELLER_MAIL_DATETIME =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final JavaMailSender javaMailSender;

    @Async
    public void sendApprovalEmail(String to, String marketName, LocalDateTime processedAt) {
        String safeMarket = HtmlUtils.htmlEscape(marketName);
        String when = processedAt.format(SELLER_MAIL_DATETIME);
        String subject = "[SHOWROOMZ] 입점 신청이 승인되었습니다.";
        String text = String.format("""
                <html>
                <body style="font-family: sans-serif; line-height: 1.6; color: #222;">
                    <p>안녕하세요, %s 담당자님.</p>
                    <p>SHOWROOMZ 입점 신청이 승인되었습니다. 이제 마켓 어드민 페이지에 접속하여 상품 등록 및 운영을 시작하실 수 있습니다.</p>
                    <p><strong>■ 승인 정보</strong><br/>
                    마켓명: %s<br/>
                    승인일시: %s<br/>
                    담당 관리자: %s</p>
                    <p><strong>■ 시작하기</strong><br/>
                    아래 버튼을 클릭하여 마켓 어드민 페이지에 접속해 주세요.</p>
                    <p><a href="%s" style="display:inline-block;padding:10px 18px;background:#111;color:#fff;text-decoration:none;border-radius:4px;">마켓 어드민 바로가기</a></p>
                    <p><strong>■ 이용 안내</strong><br/>
                    상품 등록은 마켓 어드민 &gt; 상품 관리 &gt; 상품 등록에서 진행하실 수 있습니다.<br/>
                    정산은 매월 말일 기준으로 익월 15일에 등록하신 계좌로 지급됩니다.<br/>
                    서비스 이용 중 문의 사항은 마켓 어드민 내 1:1 문의를 이용해 주세요.</p>
                    <p>감사합니다.<br/>%s 드림</p>
                </body>
                </html>
                """,
                safeMarket,
                safeMarket,
                when,
                OPS_TEAM,
                SELLER_ADMIN_LOGIN_URL,
                OPS_TEAM);

        sendEmail(to, subject, text);
    }

    @Async
    public void sendRejectionEmail(String to, String marketName, LocalDateTime processedAt,
                                   String reasonSummary, String reasonDetail) {
        String safeMarket = HtmlUtils.htmlEscape(marketName);
        String safeSummary = HtmlUtils.htmlEscape(reasonSummary);
        String when = processedAt.format(SELLER_MAIL_DATETIME);
        String detailBlock;
        if (reasonDetail != null && !reasonDetail.isBlank()) {
            detailBlock = HtmlUtils.htmlEscape(reasonDetail.strip());
        } else {
            detailBlock = "";
        }
        String subject = "[SHOWROOMZ] 입점 신청이 반려되었습니다.";
        String text = String.format("""
                <html>
                <body style="font-family: sans-serif; line-height: 1.6; color: #222;">
                    <p>안녕하세요, %s 담당자님.</p>
                    <p>SHOWROOMZ 입점 신청을 검토한 결과, 아래와 같은 사유로 반려 처리되었습니다.</p>
                    <p><strong>■ 반려 정보</strong><br/>
                    마켓명: %s<br/>
                    반려일시: %s<br/>
                    담당 관리자: %s</p>
                    <p><strong>■ 반려 사유</strong> %s</p>
                    <p><strong>■ 상세 내용</strong><br/>%s</p>
                    <p><strong>■ 재신청 안내</strong><br/>
                    반려 사유를 확인하신 후 해당 내용을 수정하여 재신청하실 수 있습니다. 재신청은 SHOWROOMZ 입점 신청 페이지에서 진행해 주세요.</p>
                    <p><a href="%s" style="display:inline-block;padding:10px 18px;background:#111;color:#fff;text-decoration:none;border-radius:4px;">재신청 바로가기</a></p>
                    <p><strong>■ 유의사항</strong><br/>
                    재신청 시 반려 사유에 해당하는 서류 및 정보를 반드시 정확하게 기재해 주세요. 동일한 사유로 반려될 경우 입점 신청이 제한될 수 있습니다. 추가 문의 사항은 SHOWROOMZ 고객센터로 연락해 주세요.</p>
                    <p>감사합니다.<br/>%s 드림</p>
                </body>
                </html>
                """,
                safeMarket,
                safeMarket,
                when,
                OPS_TEAM,
                safeSummary,
                detailBlock,
                SELLER_REAPPLY_URL,
                OPS_TEAM);

        sendEmail(to, subject, text);
    }

    @Async
    public void sendCreatorApprovalEmail(String to, String nickname, LocalDateTime processedAt) {
        String safeName = HtmlUtils.htmlEscape(nickname);
        String when = processedAt.format(SELLER_MAIL_DATETIME);
        String subject = "[SHOWROOMZ] 크리에이터 입점 신청이 승인되었습니다.";
        String text = String.format("""
                <html>
                <body style="font-family: sans-serif; line-height: 1.6; color: #222;">
                    <p>안녕하세요, %s 크리에이터님.</p>
                    <p>SHOWROOMZ 크리에이터 입점 신청이 승인되었습니다. 지금 바로 쇼룸을 꾸미고 활동을 시작해 보세요.</p>
                    <p><strong>■ 승인 정보</strong><br/>
                    활동명: %s<br/>
                    승인일시: %s<br/>
                    <p><strong>■ 시작하기</strong><br/>
                    아래 버튼을 클릭하여 크리에이터 센터에 접속해 주세요.</p>
                    <p><a href="%s" style="display:inline-block;padding:10px 18px;background:#111;color:#fff;text-decoration:none;border-radius:4px;">크리에이터 센터 바로가기</a></p>
                    <p>감사합니다.<br/>%s 드림</p>
                </body>
                </html>
                """,
                safeName, safeName, when, CREATOR_LOGIN_URL, OPS_TEAM);

        sendEmail(to, subject, text);
    }

    /**
     * 인플루언서 입점 반려 통지(1021 기-36) — 정형 사유 · 반려일 · 재신청 가능일만. 상세 사유는 보내지 않는다.
     * 문안은 {@code CreatorRejectionNotice} 가 만든다(어드민 미리보기와 같은 문안).
     */
    @Async
    public void sendCreatorRejectionNotice(String to, String subject, String htmlBody) {
        sendEmail(to, subject, htmlBody);
    }

    /** @deprecated 상세 사유를 신청자에게 보낸다 — 1021 기-36 으로 {@link #sendCreatorRejectionNotice} 로 대체. */
    @Deprecated
    @Async
    public void sendCreatorRejectionEmail(String to, String nickname, LocalDateTime processedAt,
                                          String reasonSummary, String reasonDetail) {
        String safeName = HtmlUtils.htmlEscape(nickname);
        String safeSummary = HtmlUtils.htmlEscape(reasonSummary);
        String when = processedAt.format(SELLER_MAIL_DATETIME);
        String detailBlock = (reasonDetail != null && !reasonDetail.isBlank())
                ? HtmlUtils.htmlEscape(reasonDetail.strip()) : "내용 없음";

        String subject = "[SHOWROOMZ] 크리에이터 입점 신청이 반려되었습니다.";
        String text = String.format("""
                <html>
                <body style="font-family: sans-serif; line-height: 1.6; color: #222;">
                    <p>안녕하세요, %s님.</p>
                    <p>SHOWROOMZ 크리에이터 입점 신청을 검토한 결과, 아래와 같은 사유로 반려 처리되었습니다.</p>
                    <p><strong>■ 반려 정보</strong><br/>
                    반려일시: %s<br/>
                    <p><strong>■ 반려 사유</strong>: %s</p>
                    <p><strong>■ 상세 내용</strong><br/>%s</p>
                    <p><strong>■ 재신청 안내</strong><br/>
                    반려일로부터 14일이 지난 이후 마이페이지에서 다시 신청하실 수 있습니다. 내용을 보완하여 재신청해 주시면 신속히 검토하겠습니다.</p>
                    <p>감사합니다.<br/>%s 드림</p>
                </body>
                </html>
                """,
                safeName, when, safeSummary, detailBlock, OPS_TEAM);

        sendEmail(to, subject, text);
    }

    /**
     * §15-5 로그인 이메일 변경 통지 — 구 이메일로 발송한다. UI에는 노출되지 않는 BE 전용 안전장치로,
     * 계정 탈취로 이메일이 바뀌었을 때 원 소유자가 인지할 수 있게 한다.
     */
    @Async
    public void sendLoginEmailChangedNotice(String oldEmail, String newEmail, LocalDateTime changedAt) {
        String safeNewEmail = HtmlUtils.htmlEscape(newEmail);
        String when = changedAt.format(SELLER_MAIL_DATETIME);
        String subject = "[SHOWROOMZ] 로그인 이메일이 변경되었습니다.";
        String text = String.format("""
                <html>
                <body style="font-family: sans-serif; line-height: 1.6; color: #222;">
                    <p>안녕하세요.</p>
                    <p>회원님의 SHOWROOMZ 파트너센터 로그인 이메일이 변경되었습니다.</p>
                    <p><strong>■ 변경 정보</strong><br/>
                    변경일시: %s<br/>
                    변경된 이메일: %s</p>
                    <p>본인이 요청하지 않은 변경이라면 즉시 SHOWROOMZ 고객센터로 문의해 주세요.</p>
                    <p>감사합니다.<br/>%s 드림</p>
                </body>
                </html>
                """,
                when, safeNewEmail, OPS_TEAM);

        sendEmail(oldEmail, subject, text);
    }

    /** §16-4 변경 요청 승인 통지 — 수신은 로그인 이메일이다(tax 이메일 아님). */
    @Async
    public void sendChangeRequestApprovedEmail(String to, String requestCode, String changedFieldLabels, LocalDateTime processedAt) {
        String safeLabels = HtmlUtils.htmlEscape(changedFieldLabels);
        String when = processedAt.format(SELLER_MAIL_DATETIME);
        String subject = "[SHOWROOMZ] 변경 요청이 승인되었습니다.";
        String text = String.format("""
                <html>
                <body style="font-family: sans-serif; line-height: 1.6; color: #222;">
                    <p>안녕하세요, 담당자님.</p>
                    <p>요청하신 정보 변경이 승인되어 반영되었습니다.</p>
                    <p><strong>■ 처리 정보</strong><br/>
                    요청번호: %s<br/>
                    변경 항목: %s<br/>
                    승인일시: %s<br/>
                    담당 관리자: %s</p>
                    <p>파트너센터에서 반영된 정보를 확인하실 수 있습니다.</p>
                    <p>감사합니다.<br/>%s 드림</p>
                </body>
                </html>
                """,
                requestCode, safeLabels, when, OPS_TEAM, OPS_TEAM);

        sendEmail(to, subject, text);
    }

    /** §16-5 변경 요청 반려 통지. 선택 문구·상세 사유가 가공 없이 그대로 실린다. */
    @Async
    public void sendChangeRequestRejectedEmail(String to, String requestCode, LocalDateTime processedAt,
                                                String reasonSummary, String reasonDetail) {
        String safeSummary = HtmlUtils.htmlEscape(reasonSummary);
        String when = processedAt.format(SELLER_MAIL_DATETIME);
        String detailBlock = (reasonDetail != null && !reasonDetail.isBlank())
                ? HtmlUtils.htmlEscape(reasonDetail.strip()) : "";
        String subject = "[SHOWROOMZ] 변경 요청이 반려되었습니다.";
        String text = String.format("""
                <html>
                <body style="font-family: sans-serif; line-height: 1.6; color: #222;">
                    <p>안녕하세요, 담당자님.</p>
                    <p>요청하신 정보 변경 건이 아래 사유로 반려되었습니다.</p>
                    <p><strong>■ 반려 정보</strong><br/>
                    요청번호: %s<br/>
                    반려일시: %s<br/>
                    담당 관리자: %s</p>
                    <p><strong>■ 반려 사유</strong> %s</p>
                    <p><strong>■ 상세 내용</strong><br/>%s</p>
                    <p>내용을 보완하여 파트너센터에서 다시 요청해 주세요.</p>
                    <p>감사합니다.<br/>%s 드림</p>
                </body>
                </html>
                """,
                requestCode, when, OPS_TEAM, safeSummary, detailBlock, OPS_TEAM);

        sendEmail(to, subject, text);
    }

    /**
     * 탈퇴 결과 메일의 받는 쪽(55 계획서 M10 · 51 설계서 8절 D-3) — 호칭 · 처리 뒤 안내만 다르다. 회원 유형별로 메서드를 다시 만들지 않는다.
     * 브랜드 안내는 파트너 시안 M5 고지, 인플루언서 안내는 「스튜디오 자격만 해지 · 소비자 계정은 그대로」(§51-5)다.
     */
    public enum WithdrawalAudience {
        BRAND("브랜드", "파트너센터", List.of(
                "로그인이 차단되어 파트너센터에 접속할 수 없습니다.",
                "상품과 공구 게시물은 비공개로 내려갔습니다.",
                "거래 기록은 법정 보존 기간 동안 플랫폼에 남습니다(소비자 분쟁·세무 대응 근거).",
                "같은 사업자등록번호로 재가입할 수 있으나 이전 데이터는 승계되지 않습니다.")),
        CREATOR("인플루언서", "쇼룸 스튜디오", List.of(
                "쇼룸 스튜디오에 로그인할 수 없고 쇼룸은 더 이상 노출되지 않습니다.",
                "계약 · 정산 기록은 법정 보존 기간 동안 플랫폼에 남습니다.",
                "SHOWROOMZ 앱 계정은 그대로 이용할 수 있습니다.",
                "다시 활동하려면 쇼룸 스튜디오 입점을 새로 신청해 주세요."));

        private final String label;
        private final String service;
        private final List<String> approvedNotes;

        WithdrawalAudience(String label, String service, List<String> approvedNotes) {
            this.label = label;
            this.service = service;
            this.approvedNotes = approvedNotes;
        }
    }

    /** §51-5 탈퇴 승인 통지 — 시안 4-I 「결과는 이메일로 안내됩니다」. 수신처는 파기 전에 읽은 주소다. */
    @Async
    public void sendMemberWithdrawalApprovedEmail(String to, WithdrawalAudience audience, String memberName,
                                                  LocalDateTime processedAt) {
        String subject = "[SHOWROOMZ] " + audience.label + " 탈퇴가 처리되었습니다.";
        StringBuilder notes = new StringBuilder();
        for (String note : audience.approvedNotes) {
            notes.append("· ").append(HtmlUtils.htmlEscape(note)).append("<br/>");
        }
        String text = String.format("""
                <html>
                <body style="font-family: sans-serif; line-height: 1.6; color: #222;">
                    <p>안녕하세요, %s 님.</p>
                    <p>신청하신 %s 탈퇴가 처리되었습니다.</p>
                    <p><strong>■ 처리 정보</strong><br/>
                    처리일시: %s<br/>
                    담당 관리자: %s</p>
                    <p><strong>■ 안내</strong><br/>%s</p>
                    <p>그동안 SHOWROOMZ 를 이용해 주셔서 감사합니다.<br/>%s 드림</p>
                </body>
                </html>
                """,
                HtmlUtils.htmlEscape(nameOrDefault(memberName, audience)), audience.service,
                processedAt.format(SELLER_MAIL_DATETIME), OPS_TEAM, notes, OPS_TEAM);

        sendEmail(to, subject, text);
    }

    /** §51-5 탈퇴 반려 통지 — 사유 · 운영자 안내가 가공 없이 실린다. 남은 항목을 정리한 뒤 바로 다시 신청할 수 있다. */
    @Async
    public void sendMemberWithdrawalRejectedEmail(String to, WithdrawalAudience audience, String memberName,
                                                  String reasonLabel, String note, LocalDateTime processedAt) {
        String subject = "[SHOWROOMZ] " + audience.label + " 탈퇴 신청이 반려되었습니다.";
        String noteBlock = note != null && !note.isBlank()
                ? "<p><strong>■ 운영팀 안내</strong><br/>" + HtmlUtils.htmlEscape(note.strip()) + "</p>" : "";
        String text = String.format("""
                <html>
                <body style="font-family: sans-serif; line-height: 1.6; color: #222;">
                    <p>안녕하세요, %s 님.</p>
                    <p>신청하신 %s 탈퇴가 아래 사유로 반려되었습니다. 계정은 그대로 이용할 수 있습니다.</p>
                    <p><strong>■ 반려 정보</strong><br/>
                    반려일시: %s<br/>
                    담당 관리자: %s</p>
                    <p><strong>■ 반려 사유</strong> %s</p>
                    %s
                    <p>남은 항목을 정리한 뒤 %s 기본정보 관리에서 다시 신청할 수 있습니다.</p>
                    <p>감사합니다.<br/>%s 드림</p>
                </body>
                </html>
                """,
                HtmlUtils.htmlEscape(nameOrDefault(memberName, audience)), audience.service,
                processedAt.format(SELLER_MAIL_DATETIME), OPS_TEAM, HtmlUtils.htmlEscape(reasonLabel), noteBlock,
                audience.service, OPS_TEAM);

        sendEmail(to, subject, text);
    }

    private static String nameOrDefault(String memberName, WithdrawalAudience audience) {
        return memberName == null || memberName.isBlank() ? audience.label + " 담당자" : memberName;
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, true); // true = HTML
            
            javaMailSender.send(mimeMessage);
            log.info("Sent email to: {}", to);
            
        } catch (MessagingException e) {
            log.error("Failed to send email", e);
            // 메일 발송 실패가 비즈니스 로직 전체를 롤백시키지 않도록 로그만 남기거나 
            // 필요 시 예외를 던져 처리할 수 있습니다.
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
