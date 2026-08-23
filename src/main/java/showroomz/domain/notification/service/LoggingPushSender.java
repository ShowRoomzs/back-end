package showroomz.domain.notification.service;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * FCM 자격 증명이 없을 때의 대체 어댑터 — 보내지 않고 로그만 남긴다.
 *
 * <p>로컬 개발과 CI를 위해 필요하다. 서비스 계정 JSON이 없다고 서버가 뜨지 않거나 게시물
 * 등록이 실패하면 안 된다.
 *
 * <p><b>성공 0건을 돌려준다.</b> 조용히 성공으로 처리하면 이력에 "전달됨"이 찍혀서, 나중에
 * 운영에서 알림이 안 갔다는 신고가 들어왔을 때 이력을 봐도 알 수 없다.
 *
 * <p>빈 등록은 {@code FcmConfig}가 자격 증명 유무를 보고 <b>둘 중 하나만</b> 만든다.
 * {@code @ConditionalOnMissingBean}을 쓰지 않는 이유 — 컴포넌트 스캔 순서에 따라 결과가
 * 달라져서, 설정이 있는데도 로그 어댑터가 잡히는 일이 생길 수 있다.
 */
@Slf4j
public class LoggingPushSender implements PushSender {

    @Override
    public PushResult send(List<String> tokens, PushMessage message) {
        log.info("푸시 미발송(FCM 미설정) - 대상 {}건, title={}, data={}",
                tokens.size(), message.title(), message.data());
        return new PushResult(0, tokens.size(), List.of());
    }
}
