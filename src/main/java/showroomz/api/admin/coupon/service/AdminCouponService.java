package showroomz.api.admin.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.coupon.dto.*;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.repository.CouponRepository;
import showroomz.domain.coupon.repository.UserCouponRepository;
import showroomz.domain.coupon.type.CouponStatus;
import showroomz.domain.coupon.type.CouponType;
import showroomz.domain.coupon.type.ValidityType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.dto.PagingRequest;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class AdminCouponService {

    private final CouponRepository couponRepository;
    private final SellerRepository sellerRepository;
    private final UserCouponRepository userCouponRepository;
    private static final AtomicInteger ISSUE_SEQUENCE = new AtomicInteger(1);

    @Transactional(readOnly = true)
    public PageResponse<AdminCouponResponse> getCouponList(PagingRequest pagingRequest, AdminCouponSearchCondition condition) {
        Pageable pageable = pagingRequest.toPageable();
        Page<Coupon> couponPage = couponRepository.searchAdminCoupons(
                condition.getSearchType(),
                condition.getKeyword(),
                condition.getTargetAudience(),
                condition.getStatus(),
                condition.dateFromAtStartOfDay(),
                condition.dateToAtEndOfDay(),
                pageable
        );
        List<AdminCouponResponse> content = couponPage.getContent().stream()
                .map(AdminCouponResponse::from)
                .toList();
        return new PageResponse<>(content, couponPage);
    }

    @Transactional
    public Coupon createCoupon(AdminCouponCreateRequest request) {
        validateDateRules(request);

        Integer totalQty = request.getTotalQuantity();
        Integer remainingQty = (Boolean.TRUE.equals(request.getIsQuantityLimited()) && totalQty != null) ? totalQty : null;
        String issueNumber = generateCouponIssueNumber();

        Seller seller = request.getSellerId() != null
                ? sellerRepository.findById(request.getSellerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND))
                : null;

        Coupon coupon = new Coupon(
                request.getName(),
                issueNumber,
                request.getCouponType(),
                request.getTargetAudience(),
                request.getShowroomId(),
                request.getIsQuantityLimited(),
                request.getDiscountUnit(),
                request.getDiscountValue(),
                request.getMinOrderAmount(),
                request.getMaxDiscountAmount(),
                request.getIsMinOrderAmountLimited(),
                request.getIssueStartDate(),
                request.getIssueEndDate(),
                request.getValidityType(),
                request.getValidStartDate(),
                request.getValidEndDate(),
                request.getValidDays(),
                request.getStatus(),
                totalQty,
                remainingQty,
                seller
        );
        return couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public AdminCouponDetailResponse getCouponDetail(Long couponId) {
        Coupon coupon = findCoupon(couponId);
        long issuedCount = userCouponRepository.countByCouponId(couponId);
        long usedCount = userCouponRepository.countByCouponIdAndStatusUsed(couponId);
        List<AdminCouponDetailResponse.ShowroomAcceptance> list =
                coupon.getCouponType() == CouponType.SHOWROOM && coupon.getShowroomId() != null
                        ? List.of(AdminCouponDetailResponse.ShowroomAcceptance.builder()
                        .showroomId(coupon.getShowroomId())
                        .status("ACCEPTED")
                        .build())
                        : List.of();
        return AdminCouponDetailResponse.from(coupon, issuedCount, usedCount, list);
    }

    @Transactional
    public void updateCoupon(Long couponId, AdminCouponUpdateRequest request) {
        Coupon coupon = findCoupon(couponId);
        validateDateRules(request);
        validateActiveCouponRestrictedFields(coupon, request);
        coupon.updateAdminFields(
                request.getName(),
                request.getCouponType(),
                request.getTargetAudience(),
                request.getShowroomId(),
                request.getIsQuantityLimited(),
                request.getDiscountUnit(),
                request.getDiscountValue(),
                request.getMinOrderAmount(),
                request.getMaxDiscountAmount(),
                request.getIsMinOrderAmountLimited(),
                request.getIssueStartDate(),
                request.getIssueEndDate(),
                request.getValidityType(),
                request.getValidStartDate(),
                request.getValidEndDate(),
                request.getValidDays(),
                request.getStatus()
        );
    }

    @Transactional
    public AdminCouponBulkResponse bulkStop(List<Long> couponIds) {
        int affected = couponRepository.bulkUpdateStatus(couponIds, CouponStatus.STOPPED);
        return AdminCouponBulkResponse.builder().affectedCount(affected).message("쿠폰이 일괄 중지되었습니다.").build();
    }

    @Transactional
    public AdminCouponBulkResponse bulkDelete(List<Long> couponIds) {
        long before = couponRepository.countByIdIn(couponIds);
        couponRepository.deleteAllByIdInBatch(couponIds);
        return AdminCouponBulkResponse.builder().affectedCount((int) before).message("쿠폰이 일괄 삭제되었습니다.").build();
    }

    private Coupon findCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
    }

    private void validateDateRules(AdminCouponCreateRequest request) {
        if (!request.getIssueStartDate().isBefore(request.getIssueEndDate())) {
            throw new BusinessException(ErrorCode.INVALID_COUPON_VALIDITY_PERIOD);
        }
        if (request.getValidityType() == ValidityType.PERIOD) {
            if (request.getValidStartDate() == null || request.getValidEndDate() == null
                    || !request.getValidStartDate().isBefore(request.getValidEndDate())) {
                throw new BusinessException(ErrorCode.INVALID_COUPON_VALIDITY_PERIOD);
            }
        }
    }

    private void validateActiveCouponRestrictedFields(Coupon coupon, AdminCouponUpdateRequest request) {
        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            return;
        }
        boolean changed =
                coupon.getDiscountValue().compareTo(request.getDiscountValue()) != 0
                        || !java.util.Objects.equals(coupon.getMaxDiscountAmount(), request.getMaxDiscountAmount())
                        || !java.util.Objects.equals(coupon.getMinOrderAmount(), request.getMinOrderAmount())
                        || coupon.getValidityType() != request.getValidityType()
                        || !java.util.Objects.equals(coupon.getValidStartDate(), request.getValidStartDate())
                        || !java.util.Objects.equals(coupon.getValidEndDate(), request.getValidEndDate())
                        || !java.util.Objects.equals(coupon.getValidDays(), request.getValidDays());
        if (changed) {
            throw new IllegalArgumentException("ACTIVE 상태 쿠폰의 할인/최소주문금액/유효기간 정보는 수정할 수 없습니다.");
        }
    }

    private String generateCouponIssueNumber() {
        LocalDateTime now = LocalDateTime.now();
        int seq = ISSUE_SEQUENCE.getAndIncrement();
        return String.format("CPN%s%04d", now.format(java.time.format.DateTimeFormatter.ofPattern("yyMM")), seq);
    }
}
