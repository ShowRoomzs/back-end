package showroomz.api.admin.user.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import showroomz.api.admin.user.AdminMemberNumber;
import showroomz.api.admin.user.type.AdminUserSort;
import showroomz.api.admin.user.type.AdminUserTab;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.history.entity.QUserConsentHistory;
import showroomz.domain.history.entity.QUserStatusHistory;
import showroomz.domain.history.type.ConsentType;
import showroomz.domain.member.user.entity.QUserSanction;
import showroomz.domain.member.user.entity.QUsers;
import showroomz.domain.member.user.entity.UserSanction;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.notification.entity.QDeviceToken;
import showroomz.domain.order.entity.QOrder;
import showroomz.domain.order.entity.QOrderCancelRequest;
import showroomz.domain.order.entity.QOrderClaim;
import showroomz.domain.order.entity.QOrderDeliveryGroup;
import showroomz.domain.order.entity.QOrderProduct;
import showroomz.domain.order.entity.QOrderRefundTask;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.domain.payment.entity.QPayment;
import showroomz.domain.payment.entity.QPaymentCancel;
import showroomz.domain.payment.type.PaymentCancelStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 어드민 소비자 목록 조회 (§25-3).
 *
 * <p>{@code Users} 테이블은 소비자 전용이지만 소셜 로그인 직후 약관 동의를 끝내지 않은 계정이
 * {@link RoleType#GUEST}로 먼저 저장된다. 그 행들은 아직 회원이 아니라 닉네임·이름이 비어 있으므로
 * 모든 조회에서 {@link RoleType#USER}만 남긴다 — 탭 건수와 목록이 같은 모집단을 세게 하려면
 * 이 조건이 {@code where}의 맨 앞에 있어야 한다.
 */
@Repository
@RequiredArgsConstructor
public class AdminUserQueryRepository {

    private final JPAQueryFactory queryFactory;

    private static final QUsers user = QUsers.users;
    private static final QOrder order = QOrder.order;
    private static final QOrderProduct orderProduct = QOrderProduct.orderProduct;
    private static final QUserStatusHistory statusHistory = QUserStatusHistory.userStatusHistory;
    private static final QPayment payment = QPayment.payment;
    private static final QPaymentCancel paymentCancel = QPaymentCancel.paymentCancel;
    private static final QOrderClaim claim = QOrderClaim.orderClaim;
    private static final QOrderDeliveryGroup deliveryGroup = QOrderDeliveryGroup.orderDeliveryGroup;
    private static final QOrderCancelRequest cancelRequest = QOrderCancelRequest.orderCancelRequest;
    private static final QOrderRefundTask refundTask = QOrderRefundTask.orderRefundTask;
    private static final QDeviceToken deviceToken = QDeviceToken.deviceToken;
    private static final QUserConsentHistory consent = QUserConsentHistory.userConsentHistory;
    private static final QUserSanction sanction = QUserSanction.userSanction;

    /** 정지 탭 요약의 "최근 30일 신규 정지" 기준 */
    private static final int NEW_SUSPENSION_WINDOW_DAYS = 30;

    /**
     * 목록 한 행 — 마스킹 전 원본을 담는다. 마스킹은 서비스가 DTO로 옮기면서 한 번만 한다.
     *
     * @param orderCount 누적 주문. 취소만 남은 주문은 세지 않는다(아래 {@code orderCount()} 참고)
     */
    public record Row(
            Long userId,
            String nickname,
            String name,
            String phoneNumber,
            ProviderType providerType,
            LocalDateTime joinedAt,
            UserStatus status,
            long orderCount
    ) {}

    public Page<Row> search(AdminUserTab tab, String keyword, ProviderType providerType,
                            AdminUserSort sort, Pageable pageable) {
        BooleanBuilder where = createWhere(tab, keyword, providerType);
        NumberExpression<Long> orderCount = orderCount();

        List<Tuple> rows = queryFactory
                .select(user.id, user.nickname, user.name, user.phoneNumber,
                        user.providerType, user.createdAt, user.status, orderCount)
                .from(user)
                // 결제가 된 적 있는 주문만 — 결제 대기 30분 안의 주문·사전 등록만 되고 결제 안 된 주문은 누적 주문이 아니다
                // (선행 수정 계획서 3-4). 결제 후 취소된 주문은 order_product.status = CANCELLED 로 아래 조건이 거른다.
                .leftJoin(order).on(order.user.id.eq(user.id).and(order.status.in(OrderStatus.PAID_OR_LATER)))
                .leftJoin(orderProduct).on(orderProduct.order.id.eq(order.id)
                        .and(orderProduct.status.ne(OrderProductStatus.CANCELLED)))
                .where(where)
                .groupBy(user.id, user.nickname, user.name, user.phoneNumber,
                        user.providerType, user.createdAt, user.status)
                .orderBy(orderSpecifiers(sort, orderCount))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<Row> content = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            Long count = row.get(orderCount);
            content.add(new Row(
                    row.get(user.id),
                    row.get(user.nickname),
                    row.get(user.name),
                    row.get(user.phoneNumber),
                    row.get(user.providerType),
                    row.get(user.createdAt),
                    row.get(user.status),
                    count != null ? count : 0L
            ));
        }

        // 주문 조인은 LEFT라 행 수를 늘리지 않지만, 전체 건수는 조인 없이 세는 편이 싸고 오해도 없다
        Long total = queryFactory
                .select(user.count())
                .from(user)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /**
     * 탭 건수 — <b>상태 조건만 빼고</b> 검색어·가입 수단은 그대로 반영한다.
     *
     * <p>탭 숫자가 지금 보고 있는 필터와 같은 모집단을 세야 "전체 3 · 활성 2"를 눌렀을 때
     * 그 수만큼 나온다. 필터를 무시하고 전역 건수를 세면 탭을 옮길 때마다 숫자가 어긋난다.
     */
    public Map<UserStatus, Long> countByStatus(String keyword, ProviderType providerType) {
        List<Tuple> rows = queryFactory
                .select(user.status, user.count())
                .from(user)
                .where(createWhere(AdminUserTab.ALL, keyword, providerType))
                .groupBy(user.status)
                .fetch();

        Map<UserStatus, Long> counts = new EnumMap<>(UserStatus.class);
        for (UserStatus status : UserStatus.values()) {
            counts.put(status, 0L);
        }
        for (Tuple row : rows) {
            UserStatus status = row.get(user.status);
            Long count = row.get(user.count());
            if (status != null) {
                counts.put(status, count != null ? count : 0L);
            }
        }
        return counts;
    }

    /**
     * 정지 탭 요약의 "최근 30일 신규 정지" (§25-3).
     *
     * <p>정지 시각 컬럼이 따로 없어 상태 변경 이력에서 읽는다. <b>지금 정지 상태인 회원</b> 중
     * 최근 30일 안에 정지로 바뀐 이력이 있는 사람을 센다 — 정지됐다가 풀린 회원은 이 숫자에
     *들어가면 안 되고(지금 정지 탭에 없다), 같은 기간에 두 번 정지된 회원도 한 번만 센다.
     */
    public long countNewlySuspended(String keyword, ProviderType providerType, LocalDateTime now) {
        LocalDateTime since = now.minusDays(NEW_SUSPENSION_WINDOW_DAYS);

        Long count = queryFactory
                .select(user.countDistinct())
                .from(user)
                .join(statusHistory).on(statusHistory.user.id.eq(user.id)
                        .and(statusHistory.newStatus.eq(UserStatus.SUSPENDED))
                        .and(statusHistory.createdAt.goe(since)))
                .where(createWhere(AdminUserTab.SUSPENDED, keyword, providerType))
                .fetchOne();

        return count != null ? count : 0L;
    }

    /**
     * 누적 주문 — 취소되지 않은 주문 상품이 하나라도 있는 <b>주문의 수</b>.
     *
     * <p>주문 상품 수가 아니라 주문 수인 것은 화면이 "14건"을 주문 건수로 읽기 때문이다.
     * 전부 취소된 주문은 {@code orderProduct} 쪽이 null이 되어 {@code count(distinct ...)}에서
     * 자연히 빠진다.
     *
     * <p>취소 포함 여부는 기획 확인 대기(§25-3 확인필요 ⓐ)다. 잠정적으로 취소를 제외한다 —
     * 이 숫자는 계정 정지 판단의 근거로 쓰이므로 실제로 산 적 없는 건을 세면 안 된다.
     */
    private NumberExpression<Long> orderCount() {
        return orderProduct.order.id.countDistinct();
    }

    private OrderSpecifier<?>[] orderSpecifiers(AdminUserSort sort, NumberExpression<Long> orderCount) {
        AdminUserSort resolved = sort != null ? sort : AdminUserSort.RECENT_JOINED;
        return switch (resolved) {
            // 동점일 때 순서가 흔들리면 페이지를 넘길 때 같은 회원이 두 번 나온다 — 회원번호로 고정한다
            case ORDER_COUNT_DESC -> new OrderSpecifier<?>[]{orderCount.desc(), user.id.desc()};
            case MEMBER_NO -> new OrderSpecifier<?>[]{user.id.asc()};
            case RECENT_JOINED -> new OrderSpecifier<?>[]{user.createdAt.desc(), user.id.desc()};
        };
    }

    /**
     * 검색 축 판별 (§25-1) — 회원번호 · 닉네임 · 휴대폰 뒤 4자리 3축.
     *
     * <p>세 축을 OR로 묶지 않고 <b>입력 형태로 하나를 고른다.</b> 묶으면 "1234"가 닉네임에도
     * 걸려 무엇으로 찾은 결과인지 알 수 없고, 빈 상태 문구가 알려 주는 규칙(뒤 4자리)도 거짓이 된다.
     *
     * <p>이메일 축은 두지 않는다 — §25-4가 소셜 이메일 자체를 화면에서 뺐다(Apple 릴레이 주소는
     * 연락 가능한 주소가 아니다).
     */
    private BooleanBuilder createWhere(AdminUserTab tab, String keyword, ProviderType providerType) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(user.roleType.eq(RoleType.USER));

        AdminUserTab resolvedTab = tab != null ? tab : AdminUserTab.ALL;
        if (resolvedTab.getStatus() != null) {
            where.and(user.status.eq(resolvedTab.getStatus()));
        }

        if (providerType != null) {
            where.and(user.providerType.eq(providerType));
        }

        if (keyword == null || keyword.isBlank()) {
            return where;
        }
        String trimmed = keyword.trim();

        if (AdminMemberNumber.looksLikeMemberNumber(trimmed)) {
            Long userId = AdminMemberNumber.parseOrNull(trimmed);
            // 'CST-' 뒤가 숫자가 아니면 조건을 지우지 않고 0건으로 만든다 — 오타 하나에 전체 목록이 돌아오면 안 된다
            where.and(userId != null ? user.id.eq(userId) : user.id.isNull());
            return where;
        }

        if (isPhoneSuffix(trimmed)) {
            // 저장 형태가 '01012341234'든 '010-1234-5678'이든 뒤 4자리는 문자열 끝에 그대로 있다
            where.and(user.phoneNumber.endsWith(trimmed));
            return where;
        }

        where.and(user.nickname.containsIgnoreCase(trimmed));
        return where;
    }

    private boolean isPhoneSuffix(String keyword) {
        return keyword.length() == 4 && keyword.chars().allMatch(Character::isDigit);
    }

    // ------------------------------------------------------------------ 상세 거래 지표(55 계획서 M3-1)

    /**
     * 상세 거래 지표의 재료 — 금액은 결제 시점 총액과 PG 취소 성공액을 따로 담고, 순구매액은 서비스가 뺀다.
     *
     * @param orderCount      누적 주문 — 목록과 같은 식({@link #orderCount()})
     * @param cancelledAmount 그 주문 <b>자신의 결제</b>에서 성공한 PG 취소 합(전액 · 부분 · 운임 환급 모두). 중복 결제 자동 취소는 주문 결제가
     *                        아니라 빠진다 · 결제액을 넘는 계좌 송금 환급은 PG 취소가 아니라 빠진다
     * @param claimOrderCount 반품 · 교환이 접수된 주문 수({@link #countClaimOrders} · 55 계획서 M3-2 판단 메타)
     */
    public record PurchaseTotals(long paidAmount, long thisMonthPaidAmount, long cancelledAmount,
                                 long thisMonthCancelledAmount, long orderCount, long thisMonthOrderCount,
                                 LocalDateTime lastOrderAt, long claimOrderCount) {
    }

    /**
     * 한 소비자의 거래 지표. 회원 한 명의 주문은 많아야 수백 건이라 행을 읽어 합산한다. 「이번 달」은 <b>결제월</b> 기준이다 — 지난달 주문을 이번 달에
     * 취소해도 지난달 몫에서 빠진다.
     */
    public PurchaseTotals purchaseTotals(Long userId, LocalDateTime monthStart) {
        List<Tuple> paid = queryFactory
                .select(order.totalAmount, order.paidAt)
                .from(order)
                .where(order.user.id.eq(userId), order.paidAt.isNotNull())
                .fetch();
        List<Tuple> cancels = queryFactory
                .select(paymentCancel.amount, order.paidAt)
                .from(paymentCancel)
                .join(paymentCancel.payment, payment)
                .join(payment.order, order)
                .where(order.user.id.eq(userId),
                        order.paidAt.isNotNull(),
                        paymentCancel.status.eq(PaymentCancelStatus.SUCCEEDED),
                        payment.paymentId.eq(order.paidPaymentId))
                .fetch();
        List<LocalDateTime> counted = queryFactory
                .select(order.paidAt)
                .from(order)
                .where(order.user.id.eq(userId),
                        order.status.in(OrderStatus.PAID_OR_LATER),
                        JPAExpressions.selectOne().from(orderProduct)
                                .where(orderProduct.order.id.eq(order.id),
                                        orderProduct.status.ne(OrderProductStatus.CANCELLED))
                                .exists())
                .fetch();

        long paidAmount = 0;
        long thisMonthPaid = 0;
        LocalDateTime lastOrderAt = null;
        for (Tuple row : paid) {
            long amount = amountOf(row.get(order.totalAmount));
            LocalDateTime paidAt = row.get(order.paidAt);
            paidAmount += amount;
            if (isThisMonth(paidAt, monthStart)) {
                thisMonthPaid += amount;
            }
            if (paidAt != null && (lastOrderAt == null || paidAt.isAfter(lastOrderAt))) {
                lastOrderAt = paidAt;
            }
        }
        long cancelled = 0;
        long thisMonthCancelled = 0;
        for (Tuple row : cancels) {
            long amount = amountOf(row.get(paymentCancel.amount));
            cancelled += amount;
            if (isThisMonth(row.get(order.paidAt), monthStart)) {
                thisMonthCancelled += amount;
            }
        }
        long thisMonthOrders = counted.stream().filter(paidAt -> isThisMonth(paidAt, monthStart)).count();
        return new PurchaseTotals(paidAmount, thisMonthPaid, cancelled, thisMonthCancelled, counted.size(),
                thisMonthOrders, lastOrderAt, countClaimOrders(userId));
    }

    /**
     * 반품 · 교환이 접수된 <b>주문</b> 수 — 시안 「반품·교환 2건 · 14.3%」(2 ÷ 누적 주문 14)의 분자. 재발송비 결제 대기(접수 전)와 소비자가
     * 거둬들인 요청(요청 취소)은 세지 않고, 반려 종결은 센다 — 요청한 사실이 판단 근거다.
     */
    public long countClaimOrders(Long userId) {
        Long count = queryFactory
                .select(claim.orderId.countDistinct())
                .from(claim)
                .where(claim.userId.eq(userId),
                        claim.status.ne(ClaimStatus.PAYMENT_PENDING),
                        claim.result.isNull().or(claim.result.ne(ClaimResult.CANCELLED)))
                .fetchOne();
        return count != null ? count : 0L;
    }

    private static long amountOf(Integer amount) {
        return amount != null ? amount : 0L;
    }

    private static boolean isThisMonth(LocalDateTime paidAt, LocalDateTime monthStart) {
        return paidAt != null && !paidAt.isBefore(monthStart);
    }

    // ------------------------------------------------------------------ 상세 보강(55 계획서 M3-2)

    /** 「‹ 이전」 — 회원번호(id) 순 바로 앞 소비자(roleType=USER). 약관 동의 전 GUEST 는 회원이 아니라 건너뛴다. 없으면 null. */
    public Long findPreviousUserId(Long userId) {
        return queryFactory.select(user.id.max()).from(user)
                .where(user.roleType.eq(RoleType.USER), user.id.lt(userId))
                .fetchOne();
    }

    /** 「다음 ›」 — 회원번호 순 바로 뒤 소비자. 없으면 null. */
    public Long findNextUserId(Long userId) {
        return queryFactory.select(user.id.min()).from(user)
                .where(user.roleType.eq(RoleType.USER), user.id.gt(userId))
                .fetchOne();
    }

    /** 푸시를 받을 기기 토큰이 하나라도 있는가 — 알림 수신 「앱 푸시」의 전제(D15). */
    public boolean hasDeviceToken(Long userId) {
        return queryFactory.selectOne().from(deviceToken).where(deviceToken.user.id.eq(userId)).fetchFirst() != null;
    }

    /**
     * 본인인증 재인증 횟수 — C15-2 재인증은 동의 이력을 매번 남기고 {@code identity_verified_at}을 덮어쓴다. 0 이면 그 값이 가입 때의 최초
     * 인증이고, 1 이상이면 최근 갱신이다(최초 시각은 남지 않는다).
     */
    public long countIdentityReverifications(Long userId) {
        Long count = queryFactory.select(consent.count()).from(consent)
                .where(consent.user.id.eq(userId), consent.consentType.eq(ConsentType.IDENTITY_VERIFICATION),
                        consent.agreed.isTrue())
                .fetchOne();
        return count != null ? count : 0L;
    }

    /**
     * 레일 「진행 중」 칩 중 주문 축 네 가지 — 배송 중 · 취소 요청 검토 중 · 반품 · 교환 진행 · 환불 실패. 각 값은 칩이 가리키는 목록을 그 조건 +
     * {@code userId}로 부른 건수와 같아야 한다(56 C1) — 식을 목록 조건(같은 조인 · 같은 소비자 축 orders.user_id)에 맞춰 둔다.
     *
     * @param shippingOrders        배송 중 하위주문이 있는 주문 수 — 주문 목록 {@code status=SHIPPING&userId=}
     * @param cancelRequestedOrders 브랜드가 검토 중인 취소 요청이 있는 주문 수 — 주문 목록 {@code tab=CANCEL&cancelRequested=true&userId=}
     * @param openClaims            끝나지 않은 반품 · 교환 클레임(목록 행) 수 — 클레임 목록 {@code tab=ALL&openOnly=true&userId=}(기간 없음)
     * @param refundFailed          실패한 환불 작업 수 — 환불 목록 {@code tab=FAILED&userId=}
     */
    public record PendingCounts(long shippingOrders, long cancelRequestedOrders, long openClaims, long refundFailed) {
    }

    public PendingCounts pendingCounts(Long userId) {
        Long shipping = queryFactory.select(order.id.countDistinct()).from(deliveryGroup).join(deliveryGroup.order, order)
                .where(order.user.id.eq(userId), order.paidAt.isNotNull(),
                        deliveryGroup.fulfillmentStatus.eq(FulfillmentStatus.SHIPPING))
                .fetchOne();
        Long cancel = queryFactory.select(order.id.countDistinct()).from(cancelRequest)
                .join(cancelRequest.deliveryGroup, deliveryGroup).join(deliveryGroup.order, order)
                .where(order.user.id.eq(userId), order.paidAt.isNotNull(),
                        cancelRequest.status.eq(CancelRequestStatus.PENDING))
                .fetchOne();
        // 결제 대기(접수 전)는 어느 탭에도 없고, 종결은 openOnly 가 뺀다 — 행 단위(클레임)다. 탈퇴 차단은 박스 단위라 0 여부만 같다.
        Long claims = queryFactory.select(claim.count()).from(claim)
                .join(claim.deliveryGroup, deliveryGroup).join(deliveryGroup.order, order)
                .where(order.user.id.eq(userId),
                        claim.status.notIn(ClaimStatus.PAYMENT_PENDING, ClaimStatus.COMPLETED))
                .fetchOne();
        Long failed = queryFactory.select(refundTask.count()).from(refundTask).join(refundTask.order, order)
                .where(order.user.id.eq(userId), refundTask.status.eq(RefundTaskStatus.FAILED))
                .fetchOne();
        return new PendingCounts(shipping != null ? shipping : 0L, cancel != null ? cancel : 0L,
                claims != null ? claims : 0L, failed != null ? failed : 0L);
    }

    /** 정지 이력 횟수 — user_sanction 행 기준(D15 · 옛 {@code PATCH /status} 정지는 행이 없어 빠진다). 열린 정지도 센다. */
    public long countSanctions(Long userId) {
        Long count = queryFactory.select(sanction.count()).from(sanction).where(sanction.userId.eq(userId)).fetchOne();
        return count != null ? count : 0L;
    }

    /** 「직전」 정지 — 해제된 정지 중 가장 최근 것. 지금 열린 정지는 상태 카드가 보여 주므로 빼고 고른다. */
    public Optional<UserSanction> findLastClosedSanction(Long userId) {
        return Optional.ofNullable(queryFactory.selectFrom(sanction)
                .where(sanction.userId.eq(userId), sanction.releasedAt.isNotNull())
                .orderBy(sanction.suspendedAt.desc(), sanction.id.desc())
                .fetchFirst());
    }
}
