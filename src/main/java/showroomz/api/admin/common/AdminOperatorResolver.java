package showroomz.api.admin.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 운영자 식별 — 계약·공구 어드민이 함께 쓴다(32 설계 9-2). 두 벌이 되면 최고관리자 한정 정책이 확정될 때
 * 한쪽만 검사가 붙는다.
 *
 * <p>운영자 계정은 {@code Seller}({@code RoleType.ADMIN})다. 이름이 없으면 「운영자」로 스냅샷한다.
 */
@Component
@RequiredArgsConstructor
public class AdminOperatorResolver {

    private static final String DEFAULT_NAME = "운영자";

    private final SellerRepository sellers;

    /** 운영자 실명 — 이력 스냅샷·처리자 표기에 쓴다. ADMIN이 아니면 401 계열로 막는다. */
    public String operatorName(Long operatorId) {
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return sellers.findById(operatorId)
                .filter(seller -> seller.getRoleType() == RoleType.ADMIN)
                .map(seller -> seller.getName() == null ? DEFAULT_NAME : seller.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS));
    }
}
