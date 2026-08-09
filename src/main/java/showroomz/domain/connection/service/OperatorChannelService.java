package showroomz.domain.connection.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.ParticipantType;

/**
 * 운영자 고정 채널(§13-3·§14-6) 자동 생성 — 어드민 화면·API는 이번 스코프 밖이고,
 * "운영자↔브랜드 / 운영자↔쇼룸 스레드를 자동 생성해 목록 최상단에 고정"하는 부분만 담당한다.
 *
 * <p>PAIR와 달리 요청·수락 절차가 없으므로 연결은 처음부터 CONNECTED로 만들어지고 그와 동시에
 * 스레드가 열린다. 브랜드 채널은 첫 안내 메시지 없이 빈 스레드로 열린다([2026.08 변경] — 최초
 * 확정 문구("7월 정산 내역이 확정되어...")가 가입 시점과 무관한 고정값이라 오해를 유발해 제거).
 * 쇼룸(크리에이터) 채널은 "아직 연결된 브랜드가 없다"는 안내가 가입 시점에도 항상 참이므로 유지한다.
 *
 * <p>최상단 고정 자체는 목록 쿼리(MessageThreadRepository의 OPERATOR_* 우선 정렬)가 이미 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperatorChannelService {

    /**
     * 운영자 채널의 발신 주체는 개별 어드민 계정이 아니라 "SHOWROOMZ 운영팀"이라는 조직이다.
     * 어드민 API가 붙기 전까지 자동 발송 메시지의 SENDER_ID는 이 시스템 값으로 고정한다
     * (MESSAGE.SENDER_ID에는 FK가 없고, 수신자 화면에서는 SENDER_TYPE=ADMIN만으로 운영팀으로 렌더된다).
     */
    public static final long SYSTEM_OPERATOR_ID = 0L;

    /**
     * 스레드당 1건만 존재해야 하는 안내 메시지라 멱등키를 고정값으로 둔다 — UNIQUE(THREAD_ID,
     * CLIENT_MESSAGE_ID)에 걸려 백필·훅이 겹쳐 실행돼도 두 번 쌓이지 않는다(§13-10).
     */
    public static final String WELCOME_CLIENT_MESSAGE_ID = "operator-welcome";

    private static final String CREATOR_WELCOME_MESSAGE =
            "안녕하세요, %s님. 아직 연결된 브랜드가 없네요. 브랜드가 연결 요청을 보내면 [요청함] 탭에서 확인하실 수 있어요.";

    private final ConnectionRepository connectionRepository;
    private final MessageThreadService messageThreadService;

    /** 입점 승인 시 호출 — 이미 채널이 있으면 아무것도 하지 않는다. 안내 메시지 없이 빈 스레드로 연다. */
    @Transactional
    public void ensureMarketChannel(Market market) {
        if (market == null || market.getMarketName() == null || market.getMarketName().isBlank()) {
            return;
        }
        Connection connection = connectionRepository
                .findByTypeAndMarket(ConnectionType.OPERATOR_MARKET, market)
                .orElseGet(() -> connectionRepository.save(Connection.createOperatorMarket(market)));

        messageThreadService.activateThread(connection);
    }

    /**
     * 등록 완료 시 호출 — 승인 시점이 아니라 등록 완료 시점인 이유는, 안내 문구가 쇼룸명을 쓰는데
     * 쇼룸명은 등록 완료(completeRegistration)에서야 확정되기 때문이다. 연결코드 발급 시점과 같다(§13-6).
     */
    @Transactional
    public void ensureCreatorChannel(Creator creator) {
        if (creator == null || creator.getShowroomName() == null || creator.getShowroomName().isBlank()) {
            return;
        }
        Connection connection = connectionRepository
                .findByTypeAndCreator(ConnectionType.OPERATOR_CREATOR, creator)
                .orElseGet(() -> connectionRepository.save(Connection.createOperatorCreator(creator)));

        openWithWelcome(connection, CREATOR_WELCOME_MESSAGE.formatted(creator.getShowroomName()));
    }

    private void openWithWelcome(Connection connection, String welcomeMessage) {
        MessageThread thread = messageThreadService.activateThread(connection);
        messageThreadService.sendMessage(thread, ParticipantType.ADMIN, SYSTEM_OPERATOR_ID,
                WELCOME_CLIENT_MESSAGE_ID, welcomeMessage, null);
    }
}
