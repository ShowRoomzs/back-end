package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminLoginDeviceReader;
import showroomz.api.admin.common.AdminMemberMasker;
import showroomz.api.admin.common.AdminMemberNavigation;
import showroomz.api.admin.common.AdminPrivacySessions;
import showroomz.api.admin.transaction.dto.AdminOrderDto;
import showroomz.api.admin.transaction.service.AdminOrderQueryService;
import showroomz.api.admin.user.AdminMemberNumber;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.admin.user.repository.AdminUserQueryRepository;
import showroomz.api.admin.user.type.AdminUserInquiryKind;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.address.repository.DeliveryAddressRepository;
import showroomz.domain.history.type.DeviceType;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.support.OneToOneInquiryNumber;
import showroomz.domain.inquiry.support.ProductInquiryNumber;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.member.user.entity.UserPrivacyAccessLog;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserPrivacyAccessLogRepository;
import showroomz.domain.member.user.type.NotificationToggle;
import showroomz.domain.member.user.type.UserPendingChip;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.member.user.vo.NotificationSetting;
import showroomz.domain.order.type.AdminOrderTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 소비자 상세의 시안 필드 보강(55 계획서 M3-2 · ui-admin-13 rev.7) — 회원번호 · 이전/다음 · 최근 로그인 · 기기 · 알림 수신 · 본인확인 ·
 * 최근 주문 5건 · 최근 문의 3건 · 배송지 · 진행 중 칩 · 판단 메타를 한 번에 조립한다. {@link AdminUserService}가 이것 하나만 더 의존하게
 * 모았다(단위 테스트의 mock 이 하나로 끝난다).
 *
 * <ul>
 *   <li>마스킹은 서버가 끝낸다 — 본인확인 정보 · 배송지 상세 · 수령인 · 연락처는 <b>이 운영자의 유효한 열람 세션</b>이 없으면 원본을 싣지
 *   않는다(§25-1 · M3-3). 다른 운영자의 세션은 적용되지 않는다</li>
 *   <li>탈퇴는 파기 — 본인확인 · 배송지 · 알림 수신 · 최근 로그인은 값 없이 {@code destroyed}만, 주문 · 문의 기록은 보존이라 그대로 싣는다</li>
 *   <li>다른 화면이 이미 세는 숫자는 같은 식을 쓴다 — 최근 주문은 어드민 주문 목록 · 진행 중 칩은 칩이 가리키는 목록(56 C1) · 문의 번호는
 *   각 관리 화면 번호</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserDetailAssembler {

    private static final int RECENT_ORDER_LIMIT = 5;
    private static final int RECENT_INQUIRY_LIMIT = 3;
    private static final int INQUIRY_TITLE_LENGTH = 40;
    /** 알림 설정 화면(C15)의 토글 순서 — 혜택 · 이벤트는 users.marketing_agree 다. */
    private static final List<NotificationToggle> PUSH_TOGGLES =
            List.of(NotificationToggle.FOLLOW_GROUP_BUY, NotificationToggle.FOLLOW_POST, NotificationToggle.GROUP_BUY_ENDING);
    /** 진행 중 칩 이동 목록(56 C1) — {@code AdminUserDto.PendingChipLink.target}. */
    private static final String TARGET_INQUIRIES = "INQUIRIES";
    private static final String TARGET_PRODUCT_INQUIRIES = "PRODUCT_INQUIRIES";
    private static final String TARGET_ORDERS = "ORDERS";
    private static final String TARGET_CLAIMS = "CLAIMS";
    private static final String TARGET_REFUNDS = "REFUNDS";

    private final AdminUserQueryRepository queries;
    private final AdminLoginDeviceReader loginDevices;
    private final AdminOrderQueryService orderQueryService;
    private final OneToOneInquiryRepository oneToOneInquiryRepository;
    private final ProductInquiryRepository productInquiryRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final UserPrivacyAccessLogRepository privacyLogs;
    private final AdminPrivacySessions privacySessions;

    /**
     * @param trade        거래 지표(M3-1) — 판단 메타가 같은 숫자를 쓴다
     * @param inquiryCount 문의 내역 수 — 「최근 문의」의 전체 건수와 같다
     */
    public Extras assemble(Users user, AdminUserTradeReader.Trade trade, long inquiryCount, Long operatorId,
                           LocalDateTime now) {
        boolean withdrawn = user.getStatus() == UserStatus.WITHDRAWN;
        Long userId = user.getId();
        UserPrivacyAccessLog session = activeSession(userId, operatorId, now);
        boolean unmasked = session != null && !withdrawn;
        return new Extras(
                AdminMemberNumber.format(userId),
                new AdminMemberNavigation(queries.findPreviousUserId(userId), queries.findNextUserId(userId)),
                withdrawn ? null : user.getLastLoginAt(),
                withdrawn ? null : loginDevices.lastDevice(userId),
                notification(user, withdrawn),
                identity(user, withdrawn, unmasked, now),
                unmask(userId, session, withdrawn, operatorId, now),
                recentOrders(userId),
                recentInquiries(user, inquiryCount),
                addresses(user, withdrawn, unmasked),
                pendingChips(userId),
                judgement(user, trade, withdrawn, now));
    }

    // ------------------------------------------------------------------ 계정 정보 · 본인확인

    /** 앱 푸시 켬 = 기기 토큰 있음 ∧ 토글 하나라도 켬(D15). 토글 4개는 C15 화면 순서 그대로. */
    private AdminUserDto.NotificationStatus notification(Users user, boolean withdrawn) {
        if (withdrawn) {
            return new AdminUserDto.NotificationStatus(true, null, null, List.of(), null, null);
        }
        NotificationSetting setting = user.getNotificationSetting();
        List<AdminUserDto.NotificationToggleState> toggles = new ArrayList<>();
        for (NotificationToggle toggle : PUSH_TOGGLES) {
            boolean on = setting != null && setting.isOn(toggle);
            toggles.add(new AdminUserDto.NotificationToggleState(toggle, toggle.getLabel(), on,
                    on ? setting.agreedAt(toggle) : null));
        }
        toggles.add(new AdminUserDto.NotificationToggleState(NotificationToggle.BENEFIT, NotificationToggle.BENEFIT.getLabel(),
                user.isMarketingAgree(), user.isMarketingAgree() ? user.getMarketingAgreeChangedAt() : null));
        boolean deviceRegistered = queries.hasDeviceToken(user.getId());
        boolean anyOn = toggles.stream().anyMatch(AdminUserDto.NotificationToggleState::on);
        return new AdminUserDto.NotificationStatus(false, deviceRegistered && anyOn, deviceRegistered, toggles,
                user.isMarketingAgree(), user.getMarketingAgreeChangedAt());
    }

    /**
     * 본인확인 — 이름 · 생년월일 · 휴대폰은 해제 세션이 있을 때만 실값. 성별은 해제 대상이 아니다(시안 A1). {@code identity_verified_at}은
     * 재인증이 덮어쓰므로 재인증 이력이 없을 때만 「최초 인증」, 있으면 「최근 갱신」이다.
     */
    private AdminUserDto.Identity identity(Users user, boolean withdrawn, boolean unmasked, LocalDateTime now) {
        if (withdrawn) {
            return new AdminUserDto.Identity(true, false, null, null, null, null, null, null, null, null, null, null);
        }
        LocalDateTime verifiedAt = user.getIdentityVerifiedAt();
        boolean renewed = verifiedAt != null && queries.countIdentityReverifications(user.getId()) > 0;
        LocalDateTime firstVerifiedAt = renewed ? null : verifiedAt;
        LocalDateTime renewedAt = renewed ? verifiedAt : null;
        String gender = user.getGender();
        if (unmasked) {
            return new AdminUserDto.Identity(false, true, user.getName(), AdminMemberMasker.formatBirthday(user.getBirthday()),
                    AdminMemberMasker.age(user.getBirthday(), KstDates.today(now)), gender, genderLabel(gender), null,
                    AdminMemberMasker.formatPhone(user.getPhoneNumber()), null, firstVerifiedAt, renewedAt);
        }
        return new AdminUserDto.Identity(false, false, AdminMemberMasker.maskName(user.getName()),
                AdminMemberMasker.maskBirthdayYearOnly(user.getBirthday()), null, gender, genderLabel(gender), null,
                AdminMemberMasker.maskPhone(user.getPhoneNumber()), null, firstVerifiedAt, renewedAt);
    }

    /** 해제 상태 — 조립 규칙은 인플루언서 상세와 같은 {@link AdminPrivacySessions}. 직전 사유는 운영자 무관 최신(재해제 프리필). */
    private AdminUserDto.Unmask unmask(Long userId, UserPrivacyAccessLog session, boolean withdrawn, Long operatorId,
                                       LocalDateTime now) {
        AdminPrivacySessions.View view = privacySessions.view(session, withdrawn, operatorId, now);
        var lastReason = privacyLogs.findFirstByUserIdOrderByAccessedAtDescIdDesc(userId)
                .map(UserPrivacyAccessLog::getReason)
                .orElse(null);
        return new AdminUserDto.Unmask(view.allowed(), view.unmasked(), view.operatorName(), view.unmaskedAt(),
                view.expiresAt(), view.remainingSeconds(), lastReason);
    }

    /** 이 운영자가 이 소비자에 연 유효한 세션 — 회원 1건 · 운영자 1명에 묶인다(다른 소비자 상세에 실값이 새지 않는다). */
    public UserPrivacyAccessLog activeSession(Long userId, Long operatorId, LocalDateTime now) {
        if (operatorId == null) {
            return null;
        }
        return privacyLogs.findFirstByUserIdAndOperatorIdOrderByAccessedAtDescIdDesc(userId, operatorId)
                .filter(log -> log.isActive(now))
                .orElse(null);
    }

    private static String genderLabel(String gender) {
        if ("MALE".equalsIgnoreCase(gender)) {
            return "남성";
        }
        if ("FEMALE".equalsIgnoreCase(gender)) {
            return "여성";
        }
        return null;
    }

    // ------------------------------------------------------------------ 최근 주문 · 최근 문의

    /** 어드민 주문 목록을 userId 로 그대로 부른다 — 행 모양 · 집합이 「전체 주문 내역 보기」와 같아야 한다. */
    private AdminUserDto.RecentOrders recentOrders(Long userId) {
        PagingRequest paging = new PagingRequest();
        paging.setPage(1);
        paging.setSize(RECENT_ORDER_LIMIT);
        PageResponse<AdminOrderDto.ListItem> page = orderQueryService.getOrders(new AdminOrderDto.SearchParams(
                AdminOrderTab.ALL, null, null, null, null, userId, null, null, null, null, null, false), null, null, paging);
        return new AdminUserDto.RecentOrders(page.getPageInfo().getTotalResults(), page.getContent());
    }

    /** 1:1 · 상품 문의를 각각 최신 3건씩 읽어 합친 뒤 3건만 남긴다 — 번호는 남은 행에만 매긴다(번호 1건 = 순번 count 1회). */
    private AdminUserDto.RecentInquiries recentInquiries(Users user, long inquiryCount) {
        PageRequest top = PageRequest.of(0, RECENT_INQUIRY_LIMIT);
        List<Object> rows = new ArrayList<>();
        rows.addAll(oneToOneInquiryRepository.findByUserOrderByCreatedAtDesc(user, top).getContent());
        rows.addAll(productInquiryRepository.findByUserId(user.getId(), top).getContent());
        List<AdminUserDto.RecentInquiry> content = rows.stream()
                .sorted(Comparator.comparing(AdminUserDetailAssembler::createdAtOf, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AdminUserDetailAssembler::idOf, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_INQUIRY_LIMIT)
                .map(this::toRecentInquiry)
                .toList();
        return new AdminUserDto.RecentInquiries(inquiryCount, content);
    }

    private AdminUserDto.RecentInquiry toRecentInquiry(Object row) {
        if (row instanceof OneToOneInquiry inquiry) {
            return new AdminUserDto.RecentInquiry(AdminUserInquiryKind.ONE_TO_ONE, AdminUserInquiryKind.ONE_TO_ONE.getLabel(),
                    inquiry.getId(), OneToOneInquiryNumber.of(inquiry, oneToOneInquiryRepository),
                    inquiry.getType() == null ? null : inquiry.getType().getDescription(), title(inquiry.getContent()),
                    inquiry.getStatus(), statusLabel(inquiry.getStatus()), inquiry.getCreatedAt());
        }
        ProductInquiry inquiry = (ProductInquiry) row;
        return new AdminUserDto.RecentInquiry(AdminUserInquiryKind.PRODUCT, AdminUserInquiryKind.PRODUCT.getLabel(),
                inquiry.getId(), ProductInquiryNumber.of(inquiry, productInquiryRepository),
                inquiry.getType() == null ? null : inquiry.getType().getDescription(), title(inquiry.getContent()),
                inquiry.getStatus(), statusLabel(inquiry.getStatus()), inquiry.getCreatedAt());
    }

    private static LocalDateTime createdAtOf(Object row) {
        return row instanceof OneToOneInquiry o ? o.getCreatedAt() : ((ProductInquiry) row).getCreatedAt();
    }

    private static Long idOf(Object row) {
        return row instanceof OneToOneInquiry o ? o.getId() : ((ProductInquiry) row).getId();
    }

    /** 제목 열이 없어 본문 앞부분을 쓴다 — 줄바꿈 · 연속 공백은 한 칸으로 접는다. */
    private static String title(String content) {
        if (content == null) {
            return null;
        }
        String flat = content.strip().replaceAll("\\s+", " ");
        return flat.length() <= INQUIRY_TITLE_LENGTH ? flat : flat.substring(0, INQUIRY_TITLE_LENGTH) + "…";
    }

    private static String statusLabel(InquiryStatus status) {
        if (status == null) {
            return null;
        }
        return status == InquiryStatus.ANSWERED ? "답변 완료" : "답변 대기";
    }

    // ------------------------------------------------------------------ 배송지

    /** 기본 배송지 먼저 · 최근 수정순. 마스킹 중에는 시 · 군 · 구까지만 — 상세 · 우편번호는 싣지 않는다(시안 A4). */
    private AdminUserDto.Addresses addresses(Users user, boolean withdrawn, boolean unmasked) {
        if (withdrawn) {
            return new AdminUserDto.Addresses(true, 0, List.of());
        }
        List<AdminUserDto.Address> content = deliveryAddressRepository.findAllByUserOrderByIsDefaultDescModifiedAtDesc(user)
                .stream()
                .map(address -> toAddress(address, unmasked))
                .toList();
        return new AdminUserDto.Addresses(false, content.size(), content);
    }

    private static AdminUserDto.Address toAddress(DeliveryAddress address, boolean unmasked) {
        if (unmasked) {
            return new AdminUserDto.Address(address.getId(), address.isDefault(), address.getAddress(),
                    address.getDetailAddress(), address.getZipCode(), address.getRecipientName(),
                    AdminMemberMasker.formatPhone(address.getPhoneNumber()), null);
        }
        return new AdminUserDto.Address(address.getId(), address.isDefault(),
                AdminMemberMasker.maskAddressToDistrict(address.getAddress()), null, null,
                AdminMemberMasker.maskName(address.getRecipientName()), AdminMemberMasker.maskPhone(address.getPhoneNumber()),
                null);
    }

    // ------------------------------------------------------------------ 레일 — 진행 중 · 판단 메타

    /**
     * 진행 중 칩 5종 — 1건 이상만(ui-admin-13 CS 응대 보강 ②). 칩마다 가리키는 목록 · 쿼리 · 건수를 {@code links}로 싣는다(56 C1) — 칩
     * 건수 = 링크 건수의 합이고, 각 링크 건수는 그 목록을 query 로 부른 결과 건수와 같은 식이다.
     */
    private List<AdminUserDto.PendingChip> pendingChips(Long userId) {
        AdminUserQueryRepository.PendingCounts orders = queries.pendingCounts(userId);
        Map<UserPendingChip, List<AdminUserDto.PendingChipLink>> links = new EnumMap<>(UserPendingChip.class);
        links.put(UserPendingChip.OPEN_INQUIRY, List.of(
                link(TARGET_INQUIRIES, userId, oneToOneInquiryRepository.countByUser_IdAndStatus(userId, InquiryStatus.WAITING),
                        "status", "WAITING"),
                link(TARGET_PRODUCT_INQUIRIES, userId, productInquiryRepository.countByUser_IdAndStatusAndExposureStatus(
                        userId, InquiryStatus.WAITING, InquiryExposureStatus.NORMAL), "status", "WAITING")));
        links.put(UserPendingChip.SHIPPING, List.of(
                link(TARGET_ORDERS, userId, orders.shippingOrders(), "status", "SHIPPING")));
        links.put(UserPendingChip.CANCEL_REQUESTED, List.of(
                link(TARGET_ORDERS, userId, orders.cancelRequestedOrders(), "tab", "CANCEL", "cancelRequested", "true")));
        links.put(UserPendingChip.RETURN_EXCHANGE, List.of(
                link(TARGET_CLAIMS, userId, orders.openClaims(), "tab", "ALL", "openOnly", "true")));
        links.put(UserPendingChip.REFUND_FAILED, List.of(
                link(TARGET_REFUNDS, userId, orders.refundFailed(), "tab", "FAILED")));
        List<AdminUserDto.PendingChip> chips = new ArrayList<>();
        links.forEach((chip, all) -> {
            List<AdminUserDto.PendingChipLink> shown = all.stream().filter(link -> link.count() > 0).toList();
            long count = shown.stream().mapToLong(AdminUserDto.PendingChipLink::count).sum();
            if (count > 0) {
                chips.add(new AdminUserDto.PendingChip(chip, chip.getLabel(), chip.getTone(), count, chip.getTarget(),
                        shown));
            }
        });
        return chips;
    }

    /** 목록 쿼리 — 탭 · 상태 쌍 다음에 userId. 값은 쿼리 문자열 그대로다. */
    private static AdminUserDto.PendingChipLink link(String target, Long userId, long count, String... pairs) {
        Map<String, String> query = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            query.put(pairs[i], pairs[i + 1]);
        }
        query.put("userId", String.valueOf(userId));
        return new AdminUserDto.PendingChipLink(target, Collections.unmodifiableMap(query), count);
    }

    /** 판단 메타 — 탈퇴 회원은 조치가 없어 거래 값을 비우고 가입 후 경과 · 정지 이력만 남긴다(시안 C1). */
    private AdminUserDto.Judgement judgement(Users user, AdminUserTradeReader.Trade trade, boolean withdrawn,
                                             LocalDateTime now) {
        AdminUserDto.PastSanction last = queries.findLastClosedSanction(user.getId())
                .map(s -> new AdminUserDto.PastSanction(s.getId(), s.getReason(),
                        s.getReason() == null ? null : s.getReason().getLabel(), s.getSuspendedAt(), s.getReleasedAt()))
                .orElse(null);
        AdminUserDto.SanctionHistory sanctions = new AdminUserDto.SanctionHistory(queries.countSanctions(user.getId()), last);
        AdminUserDto.Elapsed elapsed = elapsed(user.getCreatedAt(), now);
        if (withdrawn) {
            return new AdminUserDto.Judgement(null, null, null, null, sanctions, elapsed);
        }
        return new AdminUserDto.Judgement(trade.totalOrderCount(), trade.totalPurchaseAmount(), trade.claimOrderCount(),
                trade.claimRate(), sanctions, elapsed);
    }

    private static AdminUserDto.Elapsed elapsed(LocalDateTime joinedAt, LocalDateTime now) {
        if (joinedAt == null) {
            return null;
        }
        Period period = Period.between(KstDates.toKstDate(joinedAt), KstDates.today(now));
        return new AdminUserDto.Elapsed((int) Math.max(0, period.toTotalMonths()), Math.max(0, period.getDays()),
                Math.max(0, ChronoUnit.DAYS.between(KstDates.toKstDate(joinedAt), KstDates.today(now))));
    }

    /** 상세 응답에 더해 싣는 값 — {@code UserDetailResponse.of}가 펼친다. */
    public record Extras(String memberNo, AdminMemberNavigation navigation, LocalDateTime lastLoginAt,
                         DeviceType lastLoginDevice, AdminUserDto.NotificationStatus notification,
                         AdminUserDto.Identity identity, AdminUserDto.Unmask unmask, AdminUserDto.RecentOrders recentOrders,
                         AdminUserDto.RecentInquiries recentInquiries, AdminUserDto.Addresses addresses,
                         List<AdminUserDto.PendingChip> pendingChips, AdminUserDto.Judgement judgement) {
    }
}
