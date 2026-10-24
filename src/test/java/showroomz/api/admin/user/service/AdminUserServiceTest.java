package showroomz.api.admin.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.admin.user.repository.AdminUserQueryRepository;
import showroomz.api.admin.user.type.AdminUserSort;
import showroomz.api.admin.user.type.AdminUserTab;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.review.repository.ReviewRepository;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * §25 어드민 소비자 관리 — 목록·상세.
 *
 * <p>정지 · 해제 · 메모 · 처리 이력은 {@code ConsumerSanctionIntegrationTest}가 HTTP 부터 DB 까지 본다(51-6 설계서 12절).
 *
 * <p>목록 쪽은 이름·휴대폰이 <b>서버에서</b> 마스킹돼 나가는지가 핵심이다. 목록에는 해제 경로가
 * 없으므로(§25-1) 원본이 응답에 실릴 이유가 없다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    private static final long USER_ID = 88231L;
    private static final long OPERATOR_ID = 7L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminUserQueryRepository adminUserQueryRepository;
    @Mock
    private WishlistRepository wishlistRepository;
    @Mock
    private CreatorFollowRepository creatorFollowRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ProductInquiryRepository productInquiryRepository;
    @Mock
    private OneToOneInquiryRepository oneToOneInquiryRepository;
    @Mock
    private AdminUserStatusCardAssembler statusCardAssembler;
    @Mock
    private AdminUserMemoService memoService;
    @Mock
    private AdminUserHistoryReader historyReader;
    @Mock
    private AdminUserTradeReader tradeReader;
    @Mock
    private AdminUserDetailAssembler detailAssembler;

    @InjectMocks
    private AdminUserService adminUserService;

    @org.junit.jupiter.api.BeforeEach
    void givenNoTrade() {
        given(tradeReader.read(any(), any())).willReturn(
                new AdminUserTradeReader.Trade(0, 0, 0, 0, 0, null, null, 0, null));
        // 시안 필드 보강(55 계획서 M3-2)은 AdminConsumerDetailIntegrationTest 가 본다 — 여기서는 빈 값으로 둔다
        given(detailAssembler.assemble(any(), any(), anyLong(), any(), any())).willReturn(
                new AdminUserDetailAssembler.Extras(null, null, null, null, null,
                        new AdminUserDto.Identity(false, false, null, null, null, null, null, null, null, null, null, null),
                        null, null, null, null, List.of(), null));
    }

    private Users user(UserStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Users created = new Users("mia", "미아", "mia@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now);
        ReflectionTestUtils.setField(created, "id", USER_ID);
        created.updateStatus(status);
        return created;
    }

    private Users givenUser(UserStatus status) {
        Users found = user(status);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(found));
        return found;
    }

    @Nested
    @DisplayName("상세")
    class Detail {

        private void givenCounts(long wishlist, long follows, long reviews,
                                 long productInquiries, long oneToOneInquiries) {
            given(wishlistRepository.countByUser_Id(USER_ID)).willReturn(wishlist);
            given(creatorFollowRepository.countByUser(any())).willReturn(follows);
            given(reviewRepository.countByUser_Id(USER_ID)).willReturn(reviews);
            given(productInquiryRepository.countByUser_IdAndExposureStatusNot(
                    USER_ID, InquiryExposureStatus.DELETED)).willReturn(productInquiries);
            given(oneToOneInquiryRepository.countByUser_Id(USER_ID)).willReturn(oneToOneInquiries);
        }

        /** 문의 수는 상품 문의와 1:1 문의를 합친 값이다 — 화면에 두 줄로 나누어 두지 않았다. */
        @Test
        @DisplayName("문의 수는 상품 문의와 1:1 문의를 합쳐 보여준다")
        void inquiryCountSumsBothKinds() {
            givenUser(UserStatus.NORMAL);
            givenCounts(3, 5, 2, 4, 6);

            assertThat(adminUserService.getUserDetail(USER_ID, OPERATOR_ID).getInquiryCount()).isEqualTo(10);
        }

        /** 삭제 집행된 문의는 소비자 화면에서 내려간 글이라 활동 수에도 세지 않는다. */
        @Test
        @DisplayName("삭제 집행된 상품 문의는 활동 수에서 빠진다")
        void deletedProductInquiriesAreExcluded() {
            givenUser(UserStatus.NORMAL);
            givenCounts(0, 0, 0, 0, 0);

            adminUserService.getUserDetail(USER_ID, OPERATOR_ID);

            verify(productInquiryRepository).countByUser_IdAndExposureStatusNot(
                    USER_ID, InquiryExposureStatus.DELETED);
        }

        @Test
        @DisplayName("활동 수가 그대로 실려 내려간다")
        void activityCountsArePassedThrough() {
            givenUser(UserStatus.NORMAL);
            givenCounts(3, 5, 2, 0, 0);

            AdminUserDto.UserDetailResponse response = adminUserService.getUserDetail(USER_ID, OPERATOR_ID);

            assertThat(response.getProductWishlistCount()).isEqualTo(3);
            assertThat(response.getFollowedShowroomCount()).isEqualTo(5);
            assertThat(response.getWrittenReviewCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("없는 회원의 상세는 404다")
        void unknownUserDetailIsRejected() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.getUserDetail(USER_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("목록")
    class ListUsers {

        private final Pageable pageable = PageRequest.of(0, 20);

        private void givenRows(AdminUserQueryRepository.Row... rows) {
            given(adminUserQueryRepository.search(any(), any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of(rows), pageable, rows.length));
            Map<UserStatus, Long> counts = new EnumMap<>(UserStatus.class);
            counts.put(UserStatus.NORMAL, 7L);
            counts.put(UserStatus.SUSPENDED, 2L);
            counts.put(UserStatus.WITHDRAWN, 1L);
            given(adminUserQueryRepository.countByStatus(any(), any())).willReturn(counts);
        }

        private AdminUserQueryRepository.Row row(String name, String phone) {
            return new AdminUserQueryRepository.Row(
                    USER_ID, "미아", name, phone, ProviderType.LOCAL,
                    LocalDateTime.now(), UserStatus.NORMAL, 4L);
        }

        /** 목록에는 해제 경로가 없으므로 원본이 응답 페이로드에 실릴 이유가 없다 (§25-1). */
        @Test
        @DisplayName("이름과 휴대폰은 마스킹된 값만 내려간다")
        void nameAndPhoneAreMasked() {
            givenRows(row("김수민", "01012345678"));

            AdminUserDto.ListItem item = adminUserService.getUsers(
                    null, null, null, null, pageable).getContent().get(0);

            assertThat(item.getMaskedName()).isNotEqualTo("김수민");
            assertThat(item.getMaskedPhone()).isNotEqualTo("01012345678");
            assertThat(item.getMaskedName()).contains("*");
            assertThat(item.getMaskedPhone()).contains("*");
        }

        @Test
        @DisplayName("회원번호는 회원 ID로 만들어 붙는다 — CS가 눈으로 대조하는 값이다")
        void memberNumberIsDerivedFromId() {
            givenRows(row("김수민", "01012345678"));

            assertThat(adminUserService.getUsers(null, null, null, null, pageable)
                    .getContent().get(0).getMemberNo()).contains(String.valueOf(USER_ID));
        }

        @Test
        @DisplayName("탭을 지정하지 않으면 전체 탭으로 조회한다")
        void tabDefaultsToAll() {
            givenRows(row("김수민", "01012345678"));

            adminUserService.getUsers(null, null, null, null, pageable);

            verify(adminUserQueryRepository).search(
                    org.mockito.ArgumentMatchers.eq(AdminUserTab.ALL), any(), any(), any(), any());
        }

        /** 탭 숫자가 지금 보고 있는 범위와 같은 모집단을 세야 탭을 눌렀을 때 그 수만큼 나온다. */
        @Test
        @DisplayName("요약 건수는 상태별로 나뉘고 전체는 그 합이다")
        void summarySplitsByStatus() {
            givenRows(row("김수민", "01012345678"));

            AdminUserDto.ListSummary summary = adminUserService.getUsers(
                    AdminUserTab.ALL, null, null, AdminUserSort.RECENT_JOINED, pageable).getSummary();

            assertThat(summary.getActive()).isEqualTo(7);
            assertThat(summary.getSuspended()).isEqualTo(2);
            assertThat(summary.getWithdrawn()).isEqualTo(1);
            assertThat(summary.getTotal()).isEqualTo(10);
        }

        /**
         * 정지 탭에서만 "최근 30일 신규 정지"를 덧붙인다 — 다른 탭에서 null이어야 화면이 그 행을
         * 아예 그리지 않는다.
         */
        @Test
        @DisplayName("최근 30일 신규 정지는 정지 탭에서만 채워진다")
        void newlySuspendedOnlyOnSuspendedTab() {
            givenRows(row("김수민", "01012345678"));
            given(adminUserQueryRepository.countNewlySuspended(any(), any(), any())).willReturn(3L);

            assertThat(adminUserService.getUsers(AdminUserTab.SUSPENDED, null, null, null, pageable)
                    .getSummary().getNewSuspendedIn30Days()).isEqualTo(3);

            assertThat(adminUserService.getUsers(AdminUserTab.ALL, null, null, null, pageable)
                    .getSummary().getNewSuspendedIn30Days()).isNull();
        }

        @Test
        @DisplayName("정지 탭이 아니면 신규 정지 건수를 아예 조회하지 않는다")
        void newlySuspendedIsNotQueriedOnOtherTabs() {
            givenRows(row("김수민", "01012345678"));

            adminUserService.getUsers(AdminUserTab.ALL, null, null, null, pageable);

            verify(adminUserQueryRepository, never()).countNewlySuspended(any(), any(), any());
        }

        /** 마스킹 유틸이 값 없는 경우를 null로 돌려주므로 목록에 빈 칸이 생겨도 터지지 않아야 한다. */
        @Test
        @DisplayName("이름·휴대폰이 없는 회원도 목록에 실린다")
        void rowWithoutNameOrPhoneSurvives() {
            givenRows(row(null, null));

            AdminUserDto.ListItem item = adminUserService.getUsers(
                    null, null, null, null, pageable).getContent().get(0);

            assertThat(item.getUserId()).isEqualTo(USER_ID);
        }
    }
}
