package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.admin.user.AdminMemberNumber;
import showroomz.api.admin.user.AdminUserMasker;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.admin.user.dto.AdminUserMemoUpdateRequest;
import showroomz.api.admin.user.repository.AdminUserQueryRepository;
import showroomz.api.admin.user.type.AdminUserSort;
import showroomz.api.admin.user.type.AdminUserTab;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.history.entity.UserStatusHistory;
import showroomz.domain.history.repository.UserStatusHistoryRepository;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.review.repository.ReviewRepository;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminUserQueryRepository adminUserQueryRepository;
    private final WishlistRepository wishlistRepository;
    private final CreatorFollowRepository creatorFollowRepository;
    private final ReviewRepository reviewRepository;
    private final ProductInquiryRepository productInquiryRepository;
    private final OneToOneInquiryRepository oneToOneInquiryRepository;
    private final UserStatusHistoryRepository userStatusHistoryRepository;

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
                .maskedName(AdminUserMasker.maskName(row.name()))
                .maskedPhone(AdminUserMasker.maskPhoneNumber(row.phoneNumber()))
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
     * 유저 상세 정보 조회
     */
    public AdminUserDto.UserDetailResponse getUserDetail(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long wishlistCount = wishlistRepository.countByUser_Id(userId);
        long followedShowroomCount = creatorFollowRepository.countByUser(user);
        long reviewCount = reviewRepository.countByUser_Id(userId);
        long productInquiryCount = productInquiryRepository.countByUser_IdAndExposureStatusNot(userId, InquiryExposureStatus.DELETED);
        long oneToOneInquiryCount = oneToOneInquiryRepository.countByUser_Id(userId);
        long inquiryCount = productInquiryCount + oneToOneInquiryCount;

        List<AdminUserDto.UserStatusHistoryDto> statusHistory = userStatusHistoryRepository
                .findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(history -> AdminUserDto.UserStatusHistoryDto.builder()
                        .status(history.getNewStatus())
                        .changedAt(history.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return AdminUserDto.UserDetailResponse.of(
                user,
                wishlistCount,
                followedShowroomCount,
                reviewCount,
                inquiryCount,
                statusHistory
        );
    }

    @Transactional
    public void updateAdminMemo(Long userId, AdminUserMemoUpdateRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updateAdminMemo(request.getAdminMemo());
    }

    /**
     * 유저 상태 변경 (정지 및 활성화)
     */
    @Transactional // 쓰기 작업을 위해 클래스 레벨의 readOnly = true 설정을 덮어씀
    public void updateUserStatus(Long userId, AdminUserDto.UserStatusUpdateRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserStatus newStatus = request.getStatus();

        // 바꾸기 전에 붙잡아 둔다 — updateStatus 뒤에 읽으면 새 상태가 나와 전이 기록이 무의미해진다.
        UserStatus previousStatus = user.getStatus();

        // 이전 상태와 새 상태가 동일하다면 변경 불필요
        if (newStatus == previousStatus) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미 해당 상태로 설정되어 있습니다.");
        }


        // 요구사항: 변경 가능한 상태를 정지 상태(SUSPENDED)와 활성 상태(NORMAL)로만 제한
        if (newStatus != UserStatus.NORMAL && newStatus != UserStatus.SUSPENDED) {
            throw new IllegalArgumentException("유저 상태는 NORMAL(활성) 또는 SUSPENDED(정지)로만 변경할 수 있습니다.");
        }

        user.updateStatus(newStatus);

        // 유저 상태 변경 히스토리 저장 — 어디서 어디로 갔는지가 이력의 내용이다.
        userStatusHistoryRepository.save(UserStatusHistory.builder()
        .user(user)
        .previousStatus(previousStatus)
        .newStatus(newStatus)
        .reason("")
        .build());
    }
}
