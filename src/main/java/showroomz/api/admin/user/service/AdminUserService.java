package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.admin.user.AdminMemberNumber;
import showroomz.api.admin.common.AdminMemberMasker;
import showroomz.api.admin.common.AdminReasonOption;
import showroomz.api.admin.common.AdminSuspensionTermPreview;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.admin.user.repository.AdminUserQueryRepository;
import showroomz.api.admin.user.type.AdminUserSort;
import showroomz.api.admin.user.type.AdminUserTab;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserPrivacyAccessReason;
import showroomz.domain.member.user.type.UserReleaseReason;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.member.user.type.UserSuspensionReason;
import showroomz.domain.review.repository.ReviewRepository;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private static final int DETAIL_MEMO_LIMIT = 3;
    private static final int DETAIL_HISTORY_LIMIT = 5;

    private final UserRepository userRepository;
    private final AdminUserQueryRepository adminUserQueryRepository;
    private final WishlistRepository wishlistRepository;
    private final CreatorFollowRepository creatorFollowRepository;
    private final ReviewRepository reviewRepository;
    private final ProductInquiryRepository productInquiryRepository;
    private final OneToOneInquiryRepository oneToOneInquiryRepository;
    private final AdminUserStatusCardAssembler statusCardAssembler;
    private final AdminUserMemoService memoService;
    private final AdminUserHistoryReader historyReader;
    private final AdminUserTradeReader tradeReader;
    private final AdminUserDetailAssembler detailAssembler;
    private final showroomz.domain.member.common.service.SanctionTermPolicy termPolicy;

    /**
     * 소비자 목록 (§25-3).
     *
     * <p>이름·휴대폰은 <b>여기서 마스킹해서</b> 내려보낸다. 목록에는 해제 경로가 없으므로(§25-1)
     * 원본이 응답 페이로드에 실릴 이유가 없다 — 화면이 가리는 방식이면 개발자 도구 한 번으로
     * 전체 값이 드러나 열람 통제가 성립하지 않는다.
     *
     * <p>요약 건수는 <b>상태 조건만 빼고</b> 검색어·가입 수단 필터를 그대로 반영한다. 탭 숫자가
     * 지금 보고 있는 범위와 같은 모집단을 세야 탭을 눌렀을 때 그 수만큼 나온다.
     */
    public AdminUserDto.ListResponse getUsers(
            AdminUserTab tab, String keyword, ProviderType providerType,
            AdminUserSort sort, Pageable pageable) {

        AdminUserTab resolvedTab = tab != null ? tab : AdminUserTab.ALL;

        Page<AdminUserQueryRepository.Row> page =
                adminUserQueryRepository.search(resolvedTab, keyword, providerType, sort, pageable);

        List<AdminUserDto.ListItem> content = page.getContent().stream()
                .map(this::toListItem)
                .toList();

        return AdminUserDto.ListResponse.builder()
                .content(content)
                .pageInfo(new PaginationInfo(page))
                .summary(buildSummary(resolvedTab, keyword, providerType))
                .build();
    }

    private AdminUserDto.ListItem toListItem(AdminUserQueryRepository.Row row) {
        return AdminUserDto.ListItem.builder()
                .userId(row.userId())
                .memberNo(AdminMemberNumber.format(row.userId()))
                .nickname(row.nickname())
                .maskedName(AdminMemberMasker.maskName(row.name()))
                .maskedPhone(AdminMemberMasker.maskPhone(row.phoneNumber()))
                .providerType(row.providerType())
                .joinedAt(row.joinedAt())
                .orderCount(row.orderCount())
                .status(row.status())
                .build();
    }

    /**
     * 요약 줄 — 정지 탭에서만 "최근 30일 신규 정지"를 덧붙인다 (§25-3).
     *
     * <p>다른 탭에서 이 값을 null로 두는 것은 화면이 행 자체를 그리지 않게 하기 위해서다.
     * 전체 탭의 4분할 요약을 정지 탭에 그대로 두면 지금 보고 있는 범위와 어긋난다.
     */
    private AdminUserDto.ListSummary buildSummary(
            AdminUserTab tab, String keyword, ProviderType providerType) {

        Map<UserStatus, Long> counts = adminUserQueryRepository.countByStatus(keyword, providerType);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        Long newSuspended = tab == AdminUserTab.SUSPENDED
                ? adminUserQueryRepository.countNewlySuspended(keyword, providerType, LocalDateTime.now())
                : null;

        return AdminUserDto.ListSummary.builder()
                .total(total)
                .active(counts.getOrDefault(UserStatus.NORMAL, 0L))
                .suspended(counts.getOrDefault(UserStatus.SUSPENDED, 0L))
                .withdrawn(counts.getOrDefault(UserStatus.WITHDRAWN, 0L))
                .newSuspendedIn30Days(newSuspended)
                .build();
    }

    /**
     * 유저 상세 정보 조회 — 현재 상태 카드 · 메모 최신 3건 · 처리 이력 최신 5건을 함께 싣는다(51-6 설계서 2-3). 메모의 {@code editable}은
     * 요청 운영자 기준이다. 거래 지표 · 기본 배송지는 {@link AdminUserTradeReader}가 실제 주문 · 결제에서 낸다(55 계획서 M3-1). 시안 필드
     * (회원번호 · 이전/다음 · 최근 로그인 · 알림 수신 · 본인확인 · 최근 주문 · 최근 문의 · 배송지 · 진행 중 · 판단 메타)는
     * {@link AdminUserDetailAssembler}가 조립한다(M3-2).
     */
    public AdminUserDto.UserDetailResponse getUserDetail(Long userId, Long operatorId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long wishlistCount = wishlistRepository.countByUser_Id(userId);
        long followedShowroomCount = creatorFollowRepository.countByUser(user);
        long reviewCount = reviewRepository.countByUser_Id(userId);
        long productInquiryCount = productInquiryRepository.countByUser_IdAndExposureStatusNot(userId, InquiryExposureStatus.DELETED);
        long oneToOneInquiryCount = oneToOneInquiryRepository.countByUser_Id(userId);
        long inquiryCount = productInquiryCount + oneToOneInquiryCount;
        LocalDateTime now = LocalDateTime.now();
        AdminUserTradeReader.Trade trade = tradeReader.read(user, now);

        return AdminUserDto.UserDetailResponse.of(
                user,
                wishlistCount,
                followedShowroomCount,
                reviewCount,
                inquiryCount,
                trade,
                detailAssembler.assemble(user, trade, inquiryCount, operatorId, now),
                statusCardAssembler.statusCard(user),
                memoService.latest(user, operatorId, DETAIL_MEMO_LIMIT),
                historyReader.summary(user, DETAIL_HISTORY_LIMIT)
        );
    }

    /**
     * 정지 · 해제 · 마스킹 해제 모달의 사유 목록(55 계획서 M8 · M3-3) — enum 이 단일 소스라 FE 가 하드코딩하지 않는다. 행 모양은 브랜드 ·
     * 인플루언서와 같다({@link AdminReasonOption}). 해제 사유는 상세 입력란이 없어 {@code detailRequired}가 모두 false 이고, 운영자가 고르는
     * {@code TERM_ENDED}를 그대로 둔다(D18 — 시안 M4 3종). 마스킹 해제 사유는 정형 4종뿐이다(시안 M2).
     */
    public AdminUserDto.ReasonOptions reasonOptions() {
        return new AdminUserDto.ReasonOptions(
                AdminReasonOption.of(UserSuspensionReason.values()),
                Arrays.stream(UserReleaseReason.values())
                        .map(r -> AdminReasonOption.label(r.name(), r.getLabel()))
                        .toList(),
                Arrays.stream(UserPrivacyAccessReason.values())
                        .map(r -> AdminReasonOption.label(r.name(), r.getLabel()))
                        .toList(),
                AdminSuspensionTermPreview.of(termPolicy, LocalDateTime.now()));
    }

    /** 처리 이력 전체(51-6 설계서 7-2) — 원천 넷을 메모리에서 합친 뒤 자른다. */
    public AdminUserDto.HistoryPage getHistories(Long userId, int page, int size) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<AdminUserDto.HistoryItem> all = historyReader.read(user);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<AdminUserDto.HistoryItem> content = all.stream()
                .skip((long) (safePage - 1) * safeSize)
                .limit(safeSize)
                .toList();
        return new AdminUserDto.HistoryPage(all.size(), safePage, safeSize, content);
    }
}
