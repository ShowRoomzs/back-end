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
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 어드민이 여는 스레드의 판정(36 설계 2-1 · 44 이슈 스레드 설계서 4-4).
 *
 * <ul>
 *   <li>쓰기 — <b>운영팀 1:1 채널뿐이다.</b> 정산 조정 이슈 스레드 · 브랜드–인플루언서 1:1 스레드는 403
 *       {@code THREAD_OPERATOR_READ_ONLY}(운영자 쓰기 엔드포인트 0개 — 0-7).</li>
 *   <li>열람 — 운영팀 채널 + 정산 조정 이슈 스레드 + <b>협의가 있었던 쌍</b>의 1:1 스레드(4-5 · 상태 무관).
 *       그 밖의 쌍 스레드 · 폐기된 공구 3자 스레드(GROUP_BUY_ISSUE · FULFILLMENT)는 열지 않는다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AdminThreadAccess {

    private final MessageThreadRepository threads;
    private final AdminOperatorResolver operators;
    private final SettlementAdjustmentRepository adjustments;

    public MessageThread requireOperatorChannel(Long threadId) {
        MessageThread thread = load(threadId);
        if (!isOperatorChannel(thread)) {
            throw new BusinessException(ErrorCode.THREAD_ACCESS_DENIED);
        }
        return thread;
    }

    /** 열람 판정(4-4) — 정보 바 · 메시지 · 읽음 · 첨부 다운로드. */
    public MessageThread requireReadable(Long threadId) {
        MessageThread thread = load(threadId);
        if (isOperatorChannel(thread) || isIssueThread(thread) || isIssuePair(thread)) {
            return thread;
        }
        throw new BusinessException(ErrorCode.THREAD_ACCESS_DENIED);
    }

    /**
     * 쓰기 판정 — 운영팀 채널만 쓴다. 이슈 스레드 · 이슈 쌍의 1:1 스레드는 열람 전용(403 THREAD_OPERATOR_READ_ONLY).
     * 운영팀 채널은 스레드 상태가 아니라 회원 상태로 판정한다 — 탈퇴 회원의 채널은 열람만 된다(2-3 · 잠정).
     */
    public MessageThread requireWritableChannel(Long threadId) {
        MessageThread thread = requireReadable(threadId);
        if (!isOperatorChannel(thread)) {
            throw new BusinessException(ErrorCode.THREAD_OPERATOR_READ_ONLY);
        }
        if (!memberStatusOf(thread).isWritable()) {
            throw new BusinessException(ErrorCode.THREAD_READ_ONLY);
        }
        return thread;
    }

    public static boolean isOperatorChannel(MessageThread thread) {
        ConnectionType type = thread.getConnection().getType();
        return thread.getKind() == ThreadKind.CONNECTION
                && (type == ConnectionType.OPERATOR_MARKET || type == ConnectionType.OPERATOR_CREATOR);
    }

    public static boolean isIssueThread(MessageThread thread) {
        return thread.getKind() == ThreadKind.SETTLEMENT_ADJUSTMENT;
    }

    /** 브랜드–인플루언서 1:1 스레드 — 열람은 {@link #isIssuePair}가 판정한다. */
    public static boolean isPairThread(MessageThread thread) {
        return thread.getKind() == ThreadKind.CONNECTION && thread.getConnection().getType() == ConnectionType.PAIR;
    }

    /** 그 쌍에 정산 조정 협의가 있었는가(4-5) — 상태 무관(종결 뒤에도 CS 문의가 온다). 목록은 없고 패널 링크로만 들어온다. */
    private boolean isIssuePair(MessageThread thread) {
        Connection connection = thread.getConnection();
        return isPairThread(thread) && connection.getMarket() != null && connection.getCreator() != null
                && adjustments.existsByMarketIdAndCreatorId(connection.getMarket().getId(),
                connection.getCreator().getId());
    }

    private MessageThread load(Long threadId) {
        return threads.findWithPartiesById(threadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.THREAD_NOT_FOUND));
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
