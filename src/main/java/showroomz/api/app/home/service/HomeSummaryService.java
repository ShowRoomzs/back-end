package showroomz.api.app.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.home.dto.HomeSummaryResponse;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.cart.repository.CartRepository;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * C1 홈 상단 요약.
 *
 * <p>세는 것 두 가지가 서로 다른 도메인에 있지만 한 트랜잭션에서 뽑는다. 따로 부르면 화면이
 * 두 번 그려지고, 앱을 열 때마다 도는 경로에 왕복이 하나 더 붙는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeSummaryService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CreatorFollowRepository creatorFollowRepository;

    public HomeSummaryResponse getSummary(String username) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return HomeSummaryResponse.builder()
                .cartCount(cartRepository.countByUser(user))
                .followingCount(creatorFollowRepository.countByUser(user))
                .build();
    }
}
