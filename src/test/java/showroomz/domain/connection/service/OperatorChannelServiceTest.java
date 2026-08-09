package showroomz.domain.connection.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.message.type.ThreadStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OperatorChannelServiceTest {

    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private MessageThreadService messageThreadService;

    @InjectMocks
    private OperatorChannelService operatorChannelService;

    private final MessageThread thread = MessageThread.builder()
            .id(1L).status(ThreadStatus.OPEN).build();

    private static Market market(String name) {
        Market market = new Market();
        market.setMarketName(name);
        return market;
    }

    private static Creator creator(String showroomName) {
        return Creator.builder().id(1L).showroomName(showroomName).build();
    }

    @Test
    @DisplayName("브랜드 채널은 연결·스레드만 열고 안내 메시지를 보내지 않는다 (2026.08 변경)")
    void marketChannelOpensWithoutWelcomeMessage() {
        Market market = market("코코브라운");
        given(connectionRepository.findByTypeAndMarket(ConnectionType.OPERATOR_MARKET, market))
                .willReturn(Optional.empty());
        given(connectionRepository.save(any(Connection.class))).willAnswer(inv -> inv.getArgument(0));
        given(messageThreadService.activateThread(any())).willReturn(thread);

        operatorChannelService.ensureMarketChannel(market);

        verify(messageThreadService).activateThread(any());
        verify(messageThreadService, never()).sendMessage(any(), any(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("운영자 연결은 요청·수락 없이 처음부터 CONNECTED로 만들어진다 (§1-1)")
    void operatorConnectionIsCreatedAlreadyConnected() {
        Market market = market("코코브라운");
        given(connectionRepository.findByTypeAndMarket(ConnectionType.OPERATOR_MARKET, market))
                .willReturn(Optional.empty());
        given(connectionRepository.save(any(Connection.class))).willAnswer(inv -> inv.getArgument(0));
        given(messageThreadService.activateThread(any())).willReturn(thread);

        operatorChannelService.ensureMarketChannel(market);

        ArgumentCaptor<Connection> saved = ArgumentCaptor.forClass(Connection.class);
        verify(connectionRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(ConnectionType.OPERATOR_MARKET);
        assertThat(saved.getValue().getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(saved.getValue().getCreator()).isNull();
    }

    @Test
    @DisplayName("이미 채널이 있으면 연결을 새로 만들지 않는다")
    void existingChannelIsNotDuplicated() {
        Market market = market("코코브라운");
        Connection existing = Connection.createOperatorMarket(market);
        given(connectionRepository.findByTypeAndMarket(ConnectionType.OPERATOR_MARKET, market))
                .willReturn(Optional.of(existing));
        given(messageThreadService.activateThread(existing)).willReturn(thread);

        operatorChannelService.ensureMarketChannel(market);

        verify(connectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("쇼룸 채널은 쇼룸명을 넣은 안내 메시지를 고정 멱등키로 1건 보낸다")
    void creatorChannelSendsWelcomeMessage() {
        Creator creator = creator("뷰티_소연");
        given(connectionRepository.findByTypeAndCreator(ConnectionType.OPERATOR_CREATOR, creator))
                .willReturn(Optional.empty());
        given(connectionRepository.save(any(Connection.class))).willAnswer(inv -> inv.getArgument(0));
        given(messageThreadService.activateThread(any())).willReturn(thread);

        operatorChannelService.ensureCreatorChannel(creator);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(messageThreadService).sendMessage(
                eq(thread), eq(ParticipantType.ADMIN), eq(OperatorChannelService.SYSTEM_OPERATOR_ID),
                eq(OperatorChannelService.WELCOME_CLIENT_MESSAGE_ID), content.capture(), any());
        assertThat(content.getValue())
                .startsWith("안녕하세요, 뷰티_소연님.")
                .contains("[요청함]");
    }

    @Test
    @DisplayName("쇼룸명이 아직 없으면 채널을 만들지 않는다 — 안내 문구를 만들 수 없기 때문")
    void creatorWithoutShowroomNameIsSkipped() {
        operatorChannelService.ensureCreatorChannel(creator(null));

        verify(connectionRepository, never()).save(any());
        verify(messageThreadService, never()).activateThread(any());
    }

    @Test
    @DisplayName("브랜드명이 아직 없으면 채널을 만들지 않는다")
    void marketWithoutNameIsSkipped() {
        operatorChannelService.ensureMarketChannel(market("  "));

        verify(connectionRepository, never()).save(any());
        verify(messageThreadService, never()).activateThread(any());
    }
}
