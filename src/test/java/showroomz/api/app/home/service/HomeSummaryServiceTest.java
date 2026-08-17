package showroomz.api.app.home.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.home.dto.HomeSummaryResponse;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.cart.repository.CartRepository;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * C1 홈 상단 요약.
 *
 * <p>배지 숫자를 <b>자르지 않고</b> 그대로 내려보내는 것이 계약이다 — 99+ 표기는 클라이언트 규칙이라
 * 서버가 미리 자르면 앱이 같은 숫자를 다른 화면에서 다시 쓸 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class HomeSummaryServiceTest {

    private static final String USERNAME = "mia";

    @Mock
    private UserRepository userRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CreatorFollowRepository creatorFollowRepository;

    @InjectMocks
    private HomeSummaryService homeSummaryService;

    private Users givenUser() {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users(USERNAME, "미아", "mia@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now);
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
        return user;
    }

    @Test
    @DisplayName("장바구니 수와 팔로잉 수를 함께 내려준다 — 홈이 두 번 요청하지 않도록")
    void returnsBothCountsInOneCall() {
        Users user = givenUser();
        given(cartRepository.countByUser(user)).willReturn(3L);
        given(creatorFollowRepository.countByUser(user)).willReturn(12L);

        HomeSummaryResponse response = homeSummaryService.getSummary(USERNAME);

        assertThat(response.getCartCount()).isEqualTo(3L);
        assertThat(response.getFollowingCount()).isEqualTo(12L);
    }

    @Test
    @DisplayName("아무것도 없는 신규 사용자는 0으로 내려간다 — 앱이 빈 상태를 그리는 기준이다")
    void newUserGetsZeros() {
        Users user = givenUser();
        given(cartRepository.countByUser(user)).willReturn(0L);
        given(creatorFollowRepository.countByUser(user)).willReturn(0L);

        HomeSummaryResponse response = homeSummaryService.getSummary(USERNAME);

        assertThat(response.getCartCount()).isZero();
        assertThat(response.getFollowingCount()).isZero();
    }

    /** 배지는 1~99까지만 적고 그 위는 앱이 99+로 줄인다 — 서버가 잘라 보내면 그 판단을 못 한다. */
    @Test
    @DisplayName("99를 넘는 수도 자르지 않고 실제 수를 그대로 준다")
    void countIsNotCapped() {
        Users user = givenUser();
        given(cartRepository.countByUser(user)).willReturn(1234L);
        given(creatorFollowRepository.countByUser(user)).willReturn(150L);

        HomeSummaryResponse response = homeSummaryService.getSummary(USERNAME);

        assertThat(response.getCartCount()).isEqualTo(1234L);
        assertThat(response.getFollowingCount()).isEqualTo(150L);
    }

    @Test
    @DisplayName("없는 회원이면 세지 않고 404를 낸다")
    void unknownUserIsRejectedBeforeCounting() {
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> homeSummaryService.getSummary(USERNAME))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(cartRepository, never()).countByUser(org.mockito.ArgumentMatchers.any());
        verify(creatorFollowRepository, never()).countByUser(org.mockito.ArgumentMatchers.any());
    }
}
