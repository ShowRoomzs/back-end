package showroomz.api.app.recentSearch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.recentSearch.DTO.RecentSearchResponse;
import showroomz.api.app.recentSearch.DTO.RecentSearchSyncRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.recentSearch.entitiy.RecentSearch;
import showroomz.domain.recentSearch.repository.RecentSearchRepository;
import showroomz.domain.recentSearch.type.RecentSearchType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecentSearchService {

    private final RecentSearchRepository recentSearchRepository;
    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final ConnectionRepository connectionRepository;

    /**
     * 내 최근 검색 기록 조회 — 쇼룸 행과 검색어 행이 시간순으로 섞여 내려간다.
     */
    @Transactional(readOnly = true)
    public PageResponse<RecentSearchResponse> getMyRecentSearches(String username, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = pagingRequest.toPageable();
        Page<RecentSearch> page = recentSearchRepository.findByUser(user, pageable);

        Set<Long> ongoing = findOngoingGroupBuyShowroomIds(page.getContent());

        return new PageResponse<>(page.map(recentSearch -> RecentSearchResponse.from(recentSearch, ongoing)));
    }

    /** 공구 진행 중인 쇼룸만 아바타에 로즈 링이 붙는다 — 쇼룸 행을 모아 한 번에 판별한다. */
    private Set<Long> findOngoingGroupBuyShowroomIds(List<RecentSearch> recentSearches) {
        List<Long> showroomIds = recentSearches.stream()
                .map(RecentSearch::getCreator)
                .filter(java.util.Objects::nonNull)
                .map(Creator::getId)
                .toList();

        if (showroomIds.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(connectionRepository.findCreatorIdsWithOngoingGroupBuy(showroomIds));
    }

    /**
     * [추가] 최근 검색 기록 개별 삭제
     */
    @Transactional
    public void deleteRecentSearch(String username, Long recentSearchId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 해당 ID의 검색 기록이 존재하고, 그 주인이 현재 사용자인지 확인
        RecentSearch recentSearch = recentSearchRepository.findByIdAndUser(recentSearchId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE)); // 또는 RESOURCE_NOT_FOUND

        recentSearchRepository.delete(recentSearch);
    }

    /**
     * 최근 검색 전체 삭제 — 목록 상단의 [전체 삭제].
     */
    @Transactional
    public void deleteAllRecentSearches(String username) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        recentSearchRepository.deleteByUser(user);
    }

    /**
     * 최근 검색어 저장 (upsert)
     * - 이미 존재하는 검색어라면 시간만 최신으로 갱신
     * - 없으면 새로 생성
     */
    @Transactional
    public void saveRecentSearch(String username, String keyword) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 이미 존재하는지 확인 (Optional)
        recentSearchRepository.findByUserAndTypeAndTerm(user, RecentSearchType.TERM, keyword)
            .ifPresentOrElse(
                // 1. 있으면 시간만 업데이트 (현재 시간 기준)
                existingSearch -> existingSearch.updateTimestamp(null),
                // 2. 없으면 새로 생성 (현재 시간 기준)
                () -> recentSearchRepository.save(RecentSearch.create(user, keyword, null))
            );

        // (선택) 최대 10개까지만 유지하고 싶다면, 오래된 것 삭제 로직 추가
    }

    /**
     * 최근 검색에 쇼룸 저장 (upsert)
     * - 검색 결과에서 쇼룸을 눌러 들어갔을 때 호출한다. 다음 방문 때 그 쇼룸이 최근 검색 맨 위에 남는다.
     * - 쇼룸명이 바뀌어도 같은 쇼룸이면 한 행으로 합쳐 시간만 갱신한다.
     */
    @Transactional
    public void saveRecentShowroom(String username, Long showroomId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Creator creator = creatorRepository.findById(showroomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWROOM_NOT_FOUND));

        recentSearchRepository.findByUserAndTypeAndCreator(user, RecentSearchType.SHOWROOM, creator)
                .ifPresentOrElse(
                        existing -> {
                            existing.updateTimestamp(null);
                            existing.refreshShowroomTerm();
                        },
                        () -> recentSearchRepository.save(RecentSearch.createShowroom(user, creator, null))
                );
    }

    /**
     * 검색어 목록 일괄 저장 (동기화)
     * - 요청받은 createdAt 시간으로 설정
     * - 비로그인 상태에서 쌓이는 로컬 기록은 검색어뿐이므로 TERM 행만 다룬다
     */
    @Transactional
    public void syncRecentSearches(String username, List<RecentSearchSyncRequest.RecentSearchSyncItem> items) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        for (RecentSearchSyncRequest.RecentSearchSyncItem item : items) {
            if (item == null) continue;
            String keyword = item.getKeyword();
            Instant createdAt = item.getCreatedAt();

            if (keyword == null || keyword.isBlank()) continue;

            recentSearchRepository.findByUserAndTypeAndTerm(user, RecentSearchType.TERM, keyword)
                    .ifPresentOrElse(
                            existing -> existing.updateTimestamp(createdAt),
                            () -> recentSearchRepository.save(RecentSearch.create(user, keyword, createdAt))
                    );
        }
    }
}
