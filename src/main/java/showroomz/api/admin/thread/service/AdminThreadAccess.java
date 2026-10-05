package showroomz.api.admin.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.thread.type.AdminChannelMemberStatus;
import showroomz.api.admin.thread.type.AdminChannelTab;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.type.ThreadKind;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 어드민이 여는 스레드의 판정(36 설계 2-1) — <b>운영팀 1:1 채널뿐이다.</b>
 *
 * <p>브랜드↔인플루언서 쌍 스레드의 열람(§36-9 A-2 미결)과 공구 3자 스레드(이슈 스레드 — 추후 기획 예정)는
 * 이 API가 열지 않는다. 확정되면 이 클래스에 판정을 더한다 — 엔드포인트는 그대로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class AdminThreadAccess {

    private final MessageThreadRepository threads;
    private final AdminOperatorResolver operators;

    public MessageThread requireOperatorChannel(Long threadId) {
        MessageThread thread = threads.findWithPartiesById(threadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.THREAD_NOT_FOUND));
        ConnectionType type = thread.getConnection().getType();
        if (thread.getKind() != ThreadKind.CONNECTION
                || (type != ConnectionType.OPERATOR_MARKET && type != ConnectionType.OPERATOR_CREATOR)) {
            throw new BusinessException(ErrorCode.THREAD_ACCESS_DENIED);
        }
        return thread;
    }

    /** 쓰기 판정 — 스레드 상태가 아니라 회원 상태다. 탈퇴 회원의 채널은 열람만 된다(2-3 · 잠정). */
    public MessageThread requireWritableChannel(Long threadId) {
        MessageThread thread = requireOperatorChannel(threadId);
        if (!memberStatusOf(thread).isWritable()) {
            throw new BusinessException(ErrorCode.THREAD_READ_ONLY);
        }
        return thread;
    }

    /** ADMIN 여부 검사와 이름 조회를 겸한다 — 계약 · 공구 어드민과 같은 판정이다. */
    public String operatorName(Long operatorId) {
        return operators.operatorName(operatorId);
    }

    public static AdminChannelTab tabOf(MessageThread thread) {
        return AdminChannelTab.of(thread.getConnection().getType());
    }

    public static AdminChannelMemberStatus memberStatusOf(MessageThread thread) {
        Connection connection = thread.getConnection();
        return connection.getType() == ConnectionType.OPERATOR_MARKET
                ? AdminChannelMemberStatus.of(connection.getMarket().getStatus())
                : AdminChannelMemberStatus.of(connection.getCreator().getUser().getStatus());
    }

    /** 회원 id — 브랜드는 마켓 id, 인플루언서는 크리에이터 id다(연결·소통의 보낸 사람 규칙과 같다). */
    public static Long memberIdOf(MessageThread thread) {
        Connection connection = thread.getConnection();
        return connection.getType() == ConnectionType.OPERATOR_MARKET
                ? connection.getMarket().getId()
                : connection.getCreator().getId();
    }

    public static String memberNameOf(MessageThread thread) {
        Connection connection = thread.getConnection();
        return connection.getType() == ConnectionType.OPERATOR_MARKET
                ? connection.getMarket().getMarketName()
                : connection.getCreator().getShowroomName();
    }

    public static String memberImageOf(MessageThread thread) {
        Connection connection = thread.getConnection();
        return connection.getType() == ConnectionType.OPERATOR_MARKET
                ? connection.getMarket().getMarketImageUrl()
                : connection.getCreator().getUser().getProfileImageUrl();
    }
}
