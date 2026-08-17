package showroomz.api.app.recentSearch.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.recentSearch.DTO.RecentSearchSyncRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.recentSearch.entitiy.RecentSearch;
import showroomz.domain.recentSearch.repository.RecentSearchRepository;
import showroomz.domain.recentSearch.type.RecentSearchType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * C14 최근 검색 — 검색어 행(TERM)과 쇼룸 행(SHOWROOM)이 한 목록에 섞인다.
 *
 * <p>두 종류 모두 <b>upsert</b>다: 같은 대상을 다시 검색하면 행이 늘지 않고 시각만 최신으로 밀린다.
 * 새로 저장해 버리면 같은 검색어가 목록을 가득 채우고, 10개 상한 안에서 다른 기록을 밀어낸다.
 * 쇼룸 행은 시각과 함께 이름 스냅샷도 갱신한다 — 쇼룸이 개명하면 목록도 따라가야 한다.
 */
@ExtendWith(MockitoExtension.class)
class RecentSearchServiceTest {

    private static final String USERNAME = "mia";
    private static final long SHOWROOM_ID = 5L;

    @Mock
    private RecentSearchRepository recentSearchRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private ConnectionRepository connectionRepository;

    @InjectMocks
    private RecentSearchService recentSearchService;

    private Users user;

    private Users givenUser() {
        LocalDateTime now = LocalDateTime.now();
        user = new Users(USERNAME, "미아", "mia@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now);
        ReflectionTestUtils.setField(user, "id", 7L);
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
        return user;
    }

    private Creator showroom(String showroomName) {
        Creator creator = Creator.builder()
                .id(SHOWROOM_ID)
                .showroomName(showroomName)
                .showroomAddress("soyeon")
                .build();
        return creator;
    }

    private RecentSearchSyncRequest.RecentSearchSyncItem syncItem(String keyword, Instant createdAt) {
        RecentSearchSyncRequest.RecentSearchSyncItem item = new RecentSearchSyncRequest.RecentSearchSyncItem();
        ReflectionTestUtils.setField(item, "keyword", keyword);
        ReflectionTestUtils.setField(item, "createdAt", createdAt);
        return item;
    }

    @Nested
    @DisplayName("검색어 저장")
    class SaveTerm {

        @Test
        @DisplayName("처음 쓰는 검색어는 새 행으로 쌓인다")
        void newKeywordIsInserted() {
            givenUser();
            given(recentSearchRepository.findByUserAndTypeAndTerm(user, RecentSearchType.TERM, "토너"))
                    .willReturn(Optional.empty());

            recentSearchService.saveRecentSearch(USERNAME, "토너");

            ArgumentCaptor<RecentSearch> captor = ArgumentCaptor.forClass(RecentSearch.class);
            verify(recentSearchRepository).save(captor.capture());
            assertThat(captor.getValue().getTerm()).isEqualTo("토너");
            assertThat(captor.getValue().getType()).isEqualTo(RecentSearchType.TERM);
            assertThat(captor.getValue().getCreatedAt()).isNotNull();
        }

        /** 같은 검색어가 여러 행으로 쌓이면 목록이 한 단어로 도배된다. */
        @Test
        @DisplayName("이미 있는 검색어는 행을 늘리지 않고 시각만 최신으로 밀린다")
        void existingKeywordIsTouchedNotInserted() {
            givenUser();
            RecentSearch existing = RecentSearch.create(user, "토너", Instant.now().minus(3, ChronoUnit.DAYS));
            Instant before = existing.getCreatedAt();
            given(recentSearchRepository.findByUserAndTypeAndTerm(user, RecentSearchType.TERM, "토너"))
                    .willReturn(Optional.of(existing));

            recentSearchService.saveRecentSearch(USERNAME, "토너");

            verify(recentSearchRepository, never()).save(any());
            assertThat(existing.getCreatedAt()).isAfter(before);
        }
    }

    @Nested
    @DisplayName("쇼룸 저장")
    class SaveShowroom {

        @Test
        @DisplayName("처음 들어간 쇼룸은 쇼룸 행으로 쌓이고 이름 스냅샷을 함께 남긴다")
        void newShowroomIsInserted() {
            givenUser();
            Creator creator = showroom("소연의 뷰티룸");
            given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(creator));
            given(recentSearchRepository.findByUserAndTypeAndCreator(user, RecentSearchType.SHOWROOM, creator))
                    .willReturn(Optional.empty());

            recentSearchService.saveRecentShowroom(USERNAME, SHOWROOM_ID);

            ArgumentCaptor<RecentSearch> captor = ArgumentCaptor.forClass(RecentSearch.class);
            verify(recentSearchRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(RecentSearchType.SHOWROOM);
            assertThat(captor.getValue().getCreator()).isSameAs(creator);
            assertThat(captor.getValue().getTerm()).isEqualTo("소연의 뷰티룸");
        }

        /**
         * 쇼룸이 개명한 뒤 다시 들어가면 목록에 옛 이름이 남아 있으면 안 된다 —
         * 표시는 creator에서 읽지만 스냅샷도 함께 맞춰 두는 것이 계약이다.
         */
        @Test
        @DisplayName("같은 쇼룸을 다시 누르면 한 행으로 합치고 이름 스냅샷도 최신으로 맞춘다")
        void revisitMergesAndRefreshesName() {
            givenUser();
            Creator creator = showroom("소연의 뷰티룸");
            RecentSearch existing = RecentSearch.createShowroom(user, creator, Instant.now().minus(2, ChronoUnit.DAYS));
            Instant before = existing.getCreatedAt();

            creator.updateShowroomProfile("소연 스킨케어", null, null);
            given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(creator));
            given(recentSearchRepository.findByUserAndTypeAndCreator(user, RecentSearchType.SHOWROOM, creator))
                    .willReturn(Optional.of(existing));

            recentSearchService.saveRecentShowroom(USERNAME, SHOWROOM_ID);

            verify(recentSearchRepository, never()).save(any());
            assertThat(existing.getCreatedAt()).isAfter(before);
            assertThat(existing.getTerm()).isEqualTo("소연 스킨케어");
        }

        @Test
        @DisplayName("없는 쇼룸은 최근 검색에 넣지 않는다")
        void unknownShowroomIsRejected() {
            givenUser();
            given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recentSearchService.saveRecentShowroom(USERNAME, SHOWROOM_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SHOWROOM_NOT_FOUND);

            verify(recentSearchRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("동기화 (비로그인 기록 이관)")
    class Sync {

        /** 로컬 기록은 쌓인 시각이 의미를 가지므로 서버 시각으로 덮어쓰면 순서가 뒤집힌다. */
        @Test
        @DisplayName("보내온 시각을 그대로 저장한다 — 서버 시각으로 덮어쓰지 않는다")
        void clientTimestampIsPreserved() {
            givenUser();
            Instant createdAt = Instant.now().minus(5, ChronoUnit.DAYS);
            given(recentSearchRepository.findByUserAndTypeAndTerm(user, RecentSearchType.TERM, "토너"))
                    .willReturn(Optional.empty());

            recentSearchService.syncRecentSearches(USERNAME, List.of(syncItem("토너", createdAt)));

            ArgumentCaptor<RecentSearch> captor = ArgumentCaptor.forClass(RecentSearch.class);
            verify(recentSearchRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("이미 있는 검색어는 보내온 시각으로 갱신만 한다")
        void existingKeywordIsUpdatedToClientTimestamp() {
            givenUser();
            Instant createdAt = Instant.now().minus(1, ChronoUnit.DAYS);
            RecentSearch existing = RecentSearch.create(user, "토너", Instant.now().minus(9, ChronoUnit.DAYS));
            given(recentSearchRepository.findByUserAndTypeAndTerm(user, RecentSearchType.TERM, "토너"))
                    .willReturn(Optional.of(existing));

            recentSearchService.syncRecentSearches(USERNAME, List.of(syncItem("토너", createdAt)));

            verify(recentSearchRepository, never()).save(any());
            assertThat(existing.getCreatedAt()).isEqualTo(createdAt);
        }

        /** 로컬 기록에 빈 항목이 섞여 오는 것은 정상이다 — 한 건 때문에 동기화 전체가 실패하면 안 된다. */
        @Test
        @DisplayName("빈 검색어와 null 항목은 건너뛰고 나머지는 저장한다")
        void blankAndNullItemsAreSkipped() {
            givenUser();
            given(recentSearchRepository.findByUserAndTypeAndTerm(user, RecentSearchType.TERM, "세럼"))
                    .willReturn(Optional.empty());

            recentSearchService.syncRecentSearches(USERNAME, List.of(
                    syncItem("   ", Instant.now()),
                    syncItem(null, Instant.now()),
                    syncItem("세럼", Instant.now())));

            ArgumentCaptor<RecentSearch> captor = ArgumentCaptor.forClass(RecentSearch.class);
            verify(recentSearchRepository).save(captor.capture());
            assertThat(captor.getAllValues()).hasSize(1);
            assertThat(captor.getValue().getTerm()).isEqualTo("세럼");
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("내 기록은 지울 수 있다")
        void ownRecordIsDeleted() {
            givenUser();
            RecentSearch mine = RecentSearch.create(user, "토너", Instant.now());
            given(recentSearchRepository.findByIdAndUser(1L, user)).willReturn(Optional.of(mine));

            recentSearchService.deleteRecentSearch(USERNAME, 1L);

            verify(recentSearchRepository).delete(mine);
        }

        /** 조회 자체를 (id, user)로 좁혀 남의 기록은 애초에 잡히지 않는다. */
        @Test
        @DisplayName("남의 기록은 잡히지 않아 지울 수 없다")
        void othersRecordIsNotDeletable() {
            givenUser();
            given(recentSearchRepository.findByIdAndUser(1L, user)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recentSearchService.deleteRecentSearch(USERNAME, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(recentSearchRepository, never()).delete(any(RecentSearch.class));
        }

        @Test
        @DisplayName("전체 삭제는 내 기록만 지운다")
        void clearAllIsScopedToMe() {
            givenUser();

            recentSearchService.deleteAllRecentSearches(USERNAME);

            verify(recentSearchRepository).deleteByUser(user);
        }
    }

    @Test
    @DisplayName("없는 회원이면 아무것도 저장하지 않는다")
    void unknownUserIsRejected() {
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recentSearchService.saveRecentSearch(USERNAME, "토너"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(recentSearchRepository, never()).save(any());
    }
}
