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

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final String SELLER_ADMIN_LOGIN_URL = "https://showroomz.co.kr/seller/login";
    private static final String SELLER_REAPPLY_URL = "https://showroomz.co.kr/seller/signup";
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
                    등록하신 상품은 SHOWROOMZ 운영팀의 상품 검수 후 노출됩니다.<br/>
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
    public void sendCreatorApprovalEmail(String to, String name) {
        String subject = "[Showroomz] 크리에이터 입점 신청이 승인되었습니다.";
        String text = String.format("""
                <html>
                <body>
                    <h2>안녕하세요, %s님.</h2>
                    <p>축하합니다! Showroomz 크리에이터 입점 신청이 <strong>승인</strong>되었습니다.</p>
                    <p>지금 바로 로그인하여 쇼룸을 꾸미고 활동을 시작해보세요.</p>
                    <br/>
                    <a href="https://showroomz.co.kr/seller/login">크리에이터 센터 바로가기</a>
                </body>
                </html>
                """, name);

        sendEmail(to, subject, text);
    }

    @Async
    public void sendCreatorRejectionEmail(String to, String name, String reason) {
        String subject = "[Showroomz] 크리에이터 입점 신청이 반려되었습니다.";
        String text = String.format("""
                <html>
                <body>
                    <h2>안녕하세요, %s님.</h2>
                    <p>아쉽게도 Showroomz 크리에이터 입점 신청이 <strong>반려</strong>되었습니다.</p>
                    <p><strong>반려 사유:</strong> %s</p>
                    <p>내용을 보완하여 다시 신청해주시면 신속히 검토하겠습니다.</p>
                </body>
                </html>
                """, name, reason);

        sendEmail(to, subject, text);
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
