package showroomz.api.seller.connection.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.connection.dto.ConnectRequest;
import showroomz.api.seller.connection.dto.ConnectResponse;
import showroomz.api.seller.connection.dto.ConnectionCodeCheckResponse;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerConnectionServiceTest {

    private static final String SELLER_EMAIL = "brand@showroomz.co.kr";

    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private SellerRepository sellerRepository;
    @Mock
    private MarketRepository marketRepository;
    @Mock
    private CreatorRepository creatorRepository;

    @InjectMocks
    private SellerConnectionService sellerConnectionService;

    private Market market;

    @BeforeEach
    void setUp() {
        market = new Market();
        market.setMarketName("코코브라운");
    }

    /** 인증(getMyMarket) 통과용 스텁 — 모든 공개 메서드가 먼저 거치는 경로다. */
    private void givenAuthenticatedSeller() {
        Seller seller = new Seller();
        given(sellerRepository.findByEmail(SELLER_EMAIL)).willReturn(Optional.of(seller));
        given(marketRepository.findBySeller(seller)).willReturn(Optional.of(market));
    }

    private static Creator creator(long id, UserStatus status) {
        Users user = new Users();
        user.setStatus(status);
        return Creator.builder().id(id).showroomName("뷰티_소연").user(user).build();
    }

    @Nested
    @DisplayName("연결 요청 (§13-6)")
    class RequestConnection {

        @Test
        @DisplayName("이력이 없으면 새 PAIR 연결을 REQUESTED로 만든다")
        void createsNewRequest() {
            Creator target = creator(12L, UserStatus.NORMAL);
            givenAuthenticatedSeller();
            given(creatorRepository.findById(12L)).willReturn(Optional.of(target));
            given(connectionRepository.findByTypeAndMarketAndCreator(ConnectionType.PAIR, market, target))
                    .willReturn(Optional.empty());
            given(connectionRepository.save(any(Connection.class))).willAnswer(inv -> inv.getArgument(0));

            ConnectResponse response = sellerConnectionService.requestConnection(
                    SELLER_EMAIL, request(12L, null));

            assertThat(response.getStatus()).isEqualTo(ConnectionStatus.REQUESTED);
        }

        @Test
        @DisplayName("이미 연결된 상대에게는 재요청할 수 없다")
        void alreadyConnectedIsRejected() {
            assertReRequestRejected(ConnectionStatus.CONNECTED);
        }

        @Test
        @DisplayName("이미 요청중인 상대에게는 재요청할 수 없다")
        void alreadyRequestedIsRejected() {
            assertReRequestRejected(ConnectionStatus.REQUESTED);
        }

        private void assertReRequestRejected(ConnectionStatus status) {
            Creator target = creator(12L, UserStatus.NORMAL);
            Connection existing = Connection.requestPair(market, target);
            if (status == ConnectionStatus.CONNECTED) {
                existing.markConnected();
            }
            givenAuthenticatedSeller();
            given(creatorRepository.findById(12L)).willReturn(Optional.of(target));
            given(connectionRepository.findByTypeAndMarketAndCreator(ConnectionType.PAIR, market, target))
                    .willReturn(Optional.of(existing));

            assertThatThrownBy(() -> sellerConnectionService.requestConnection(SELLER_EMAIL, request(12L, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONNECTION_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("거절·해제됐던 연결은 같은 행을 재사용한다 — 대화 기록을 보존하기 위해서다 (§13-4)")
        void rejectedConnectionRowIsReused() {
            Creator target = creator(12L, UserStatus.NORMAL);
            Connection existing = Connection.requestPair(market, target);
            existing.markRejected();
            givenAuthenticatedSeller();
            given(creatorRepository.findById(12L)).willReturn(Optional.of(target));
            given(connectionRepository.findByTypeAndMarketAndCreator(ConnectionType.PAIR, market, target))
                    .willReturn(Optional.of(existing));

            ConnectResponse response = sellerConnectionService.requestConnection(
                    SELLER_EMAIL, request(12L, null));

            assertThat(response.getStatus()).isEqualTo(ConnectionStatus.REQUESTED);
            assertThat(existing.getRespondedAt()).isNull();
            verify(connectionRepository, never()).save(any());
        }

        @Test
        @DisplayName("탈퇴한 인플루언서에게는 요청할 수 없다")
        void withdrawnCreatorIsRejected() {
            givenAuthenticatedSeller();
            given(creatorRepository.findById(12L)).willReturn(Optional.of(creator(12L, UserStatus.WITHDRAWN)));

            assertThatThrownBy(() -> sellerConnectionService.requestConnection(SELLER_EMAIL, request(12L, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONNECTION_TARGET_NOT_CONNECTABLE);
        }

        @Test
        @DisplayName("creatorId와 연결코드를 동시에 주면 거부한다")
        void ambiguousTargetIsRejected() {
            givenAuthenticatedSeller();

            assertThatThrownBy(() -> sellerConnectionService.requestConnection(
                    SELLER_EMAIL, request(12L, "ABCD234567")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONNECTION_TARGET_AMBIGUOUS);
        }

        @Test
        @DisplayName("대상을 아무것도 주지 않으면 거부한다")
        void missingTargetIsRejected() {
            givenAuthenticatedSeller();

            assertThatThrownBy(() -> sellerConnectionService.requestConnection(SELLER_EMAIL, request(null, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONNECTION_TARGET_REQUIRED);
        }

        private ConnectRequest request(Long creatorId, String code) {
            ConnectRequest request = new ConnectRequest();
            request.setCreatorId(creatorId);
            request.setConnectionCode(code);
            return request;
        }
    }

    @Nested
    @DisplayName("연결코드 확인 (§13-6)")
    class CheckConnectionCode {

        @Test
        @DisplayName("일치하는 코드가 없으면 예외가 아니라 found=false로 응답한다 — 오타는 정상 케이스다")
        void unknownCodeIsNotAnError() {
            givenAuthenticatedSeller();
            given(creatorRepository.findByConnectionCode("ABCD234567")).willReturn(Optional.empty());

            ConnectionCodeCheckResponse response =
                    sellerConnectionService.checkConnectionCode(SELLER_EMAIL, "ABCD234567");

            assertThat(response.isFound()).isFalse();
        }

        @Test
        @DisplayName("소문자로 입력해도 대문자로 정규화해 조회한다")
        void codeIsNormalisedToUpperCase() {
            givenAuthenticatedSeller();
            given(creatorRepository.findByConnectionCode("ABCD234567"))
                    .willReturn(Optional.of(creator(12L, UserStatus.NORMAL)));

            ConnectionCodeCheckResponse response =
                    sellerConnectionService.checkConnectionCode(SELLER_EMAIL, " abcd234567 ");

            assertThat(response.isFound()).isTrue();
            assertThat(response.getCreatorId()).isEqualTo(12L);
        }

        @Test
        @DisplayName("탈퇴한 인플루언서의 코드는 없는 것으로 취급한다")
        void withdrawnCreatorCodeIsTreatedAsNotFound() {
            givenAuthenticatedSeller();
            given(creatorRepository.findByConnectionCode("ABCD234567"))
                    .willReturn(Optional.of(creator(12L, UserStatus.WITHDRAWN)));

            ConnectionCodeCheckResponse response =
                    sellerConnectionService.checkConnectionCode(SELLER_EMAIL, "ABCD234567");

            assertThat(response.isFound()).isFalse();
        }

        @Test
        @DisplayName("빈 코드는 조회 없이 found=false다")
        void blankCodeShortCircuits() {
            givenAuthenticatedSeller();

            ConnectionCodeCheckResponse response = sellerConnectionService.checkConnectionCode(SELLER_EMAIL, "  ");

            assertThat(response.isFound()).isFalse();
            verify(creatorRepository, never()).findByConnectionCode(anyString());
        }
    }
}
