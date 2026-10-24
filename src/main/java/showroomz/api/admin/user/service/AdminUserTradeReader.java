package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminMemberMasker;
import showroomz.api.admin.user.repository.AdminUserQueryRepository;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.address.repository.DeliveryAddressRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.utils.Percent;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 소비자 상세의 거래 지표 · 기본 배송지(55 계획서 M3-1) — 정지 판단의 근거라 <b>실제로 산 금액</b>을 낸다.
 *
 * <ul>
 *   <li>구매액 = 결제된 주문 총액 − 그 주문 결제의 PG 취소 성공액(순결제액 · D14). 이번 달은 결제월 기준</li>
 *   <li>누적 주문 = 목록과 같은 식(취소만 남은 주문 제외) · 평균 = 순구매액 ÷ 누적 주문</li>
 *   <li>기본 배송지는 시 · 군 · 구까지만 — 상세주소는 본인확인 정보와 같은 열람 통제를 받는다(ui-admin-13 A4). 탈퇴 회원은 파기라 null</li>
 *   <li>반품 · 교환률 = 반품 · 교환이 접수된 주문 수 ÷ 누적 주문(시안 「2건 · 14.3%」 · 판단 메타 · 55 계획서 M3-2). 주문이 없으면 null</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserTradeReader {

    private final AdminUserQueryRepository queryRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;

    public Trade read(Users user, LocalDateTime now) {
        LocalDateTime monthStart = LocalDate.from(now).withDayOfMonth(1).atStartOfDay();
        AdminUserQueryRepository.PurchaseTotals totals = queryRepository.purchaseTotals(user.getId(), monthStart);
        long total = Math.max(0, totals.paidAmount() - totals.cancelledAmount());
        long thisMonth = Math.max(0, totals.thisMonthPaidAmount() - totals.thisMonthCancelledAmount());
        long average = totals.orderCount() > 0 ? total / totals.orderCount() : 0L;
        return new Trade(total, thisMonth, (int) totals.orderCount(), (int) totals.thisMonthOrderCount(), average,
                totals.lastOrderAt(), defaultAddress(user), totals.claimOrderCount(),
                Percent.oneDecimal(totals.claimOrderCount(), totals.orderCount()));
    }

    private String defaultAddress(Users user) {
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            return null;
        }
        return deliveryAddressRepository.findByUserAndIsDefaultTrue(user)
                .map(DeliveryAddress::getAddress)
                .map(AdminMemberMasker::maskAddressToDistrict)
                .orElse(null);
    }

    public record Trade(long totalPurchaseAmount, long thisMonthPurchaseAmount, int totalOrderCount,
                        int thisMonthOrderCount, long averageOrderAmount, LocalDateTime lastOrderDate,
                        String defaultAddress, long claimOrderCount, Double claimRate) {
    }
}
