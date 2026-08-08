package showroomz.api.creator.connection.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreatorConnectionServiceTest {

    private static final String CREATOR_EMAIL = "soyeon@showroomz.co.kr";
    private static final long CREATOR_ID = 12L;

    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private MessageThreadService messageThreadService;

    @InjectMocks
    private CreatorConnectionService creatorConnectionService;

    private final Creator me = Creator.builder().id(CREATOR_ID).showroomName("뷰티_소연").build();

    private void givenAuthenticatedCreator() {
        Users user = new Users();
        given(userRepository.findByUsername(CREATOR_EMAIL)).willReturn(Optional.of(user));
        given(creatorRepository.findByUser(user)).willReturn(Optional.of(me));
    }

    private static Market market() {
        Market market = new Market();
        market.setMarketName("코코브라운");
        return market;
    }

    private Connection requestedPair() {
        return Connection.requestPair(market(), me);
    }

    @Test
    @DisplayName("수락하면 CONNECTED로 전이하고 스레드를 연다 (§14-4)")
    void acceptConnectsAndOpensThread() {
        Connection connection = requestedPair();
        givenAuthenticatedCreator();
        given(connectionRepository.findById(1L)).willReturn(Optional.of(connection));

        creatorConnectionService.accept(CREATOR_EMAIL, 1L);

        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        verify(messageThreadService).activateThread(connection);
    }

    @Test
    @DisplayName("거절하면 REJECTED로만 전이하고 스레드는 열지 않는다")
    void rejectDoesNotOpenThread() {
        Connection connection = requestedPair();
        givenAuthenticatedCreator();
        given(connectionRepository.findById(1L)).willReturn(Optional.of(connection));

        creatorConnectionService.reject(CREATOR_EMAIL, 1L);

        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.REJECTED);
        verify(messageThreadService, never()).activateThread(any());
    }

    @Test
    @DisplayName("남의 연결 요청은 수락할 수 없다")
    void cannotAcceptSomeoneElsesRequest() {
        Creator someoneElse = Creator.builder().id(99L).showroomName("다른쇼룸").build();
        givenAuthenticatedCreator();
        given(connectionRepository.findById(1L))
                .willReturn(Optional.of(Connection.requestPair(market(), someoneElse)));

        assertThatThrownBy(() -> creatorConnectionService.accept(CREATOR_EMAIL, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONNECTION_ACCESS_DENIED);
    }

    @Test
    @DisplayName("운영자 고정 채널은 수락 대상이 아니다 — 요청·수락 절차 자체가 없다 (§1-1)")
    void operatorChannelCannotBeAccepted() {
        givenAuthenticatedCreator();
        given(connectionRepository.findById(1L))
                .willReturn(Optional.of(Connection.createOperatorCreator(me)));

        assertThatThrownBy(() -> creatorConnectionService.accept(CREATOR_EMAIL, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONNECTION_ACCESS_DENIED);
    }

    @Test
    @DisplayName("이미 수락한 연결을 다시 수락할 수 없다")
    void cannotAcceptTwice() {
        Connection connection = requestedPair();
        connection.markConnected();
        givenAuthenticatedCreator();
        given(connectionRepository.findById(1L)).willReturn(Optional.of(connection));

        assertThatThrownBy(() -> creatorConnectionService.accept(CREATOR_EMAIL, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONNECTION_INVALID_STATUS);
    }

    @Test
    @DisplayName("없는 연결 id는 404다")
    void unknownConnectionIsNotFound() {
        givenAuthenticatedCreator();
        given(connectionRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> creatorConnectionService.accept(CREATOR_EMAIL, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONNECTION_NOT_FOUND);
    }
}
