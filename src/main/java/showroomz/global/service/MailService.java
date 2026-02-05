package showroomz.global.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender javaMailSender;

    @Async
    public void sendApprovalEmail(String to, String name) {
        String subject = "[Showroomz] 판매자 입점 신청이 승인되었습니다.";
        String text = String.format("""
                <html>
                <body>
                    <h2>안녕하세요, %s님.</h2>
                    <p>축하합니다! Showroomz 판매자 입점 신청이 <strong>승인</strong>되었습니다.</p>
                    <p>지금 바로 로그인하여 상품을 등록하고 판매를 시작해보세요.</p>
                    <br/>
                    <a href="https://showroomz.co.kr/seller/login">판매자 센터 바로가기</a>
                </body>
                </html>
                """, name);

        sendEmail(to, subject, text);
    }

    @Async
    public void sendRejectionEmail(String to, String name, String reason) {
        String subject = "[Showroomz] 판매자 입점 신청이 반려되었습니다.";
        String text = String.format("""
                <html>
                <body>
                    <h2>안녕하세요, %s님.</h2>
                    <p>아쉽게도 Showroomz 판매자 입점 신청이 <strong>반려</strong>되었습니다.</p>
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
