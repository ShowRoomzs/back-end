package showroomz.api.app.recentSearch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.app.recentSearch.DTO.RecentSearchResponse;
import showroomz.api.app.recentSearch.DTO.RecentSearchSyncRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.recentSearch.entitiy.RecentSearch;
import showroomz.domain.recentSearch.repository.RecentSearchRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.ErrorCode;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecentSearchService {

    private final RecentSearchRepository recentSearchRepository;
    private final UserRepository userRepository;

    /**
     * 내 최근 검색 기록 조회
     */
    @Transactional(readOnly = true)
    public PageResponse<RecentSearchResponse> getMyRecentSearches(String username, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = pagingRequest.toPageable();
        Page<RecentSearch> page = recentSearchRepository.findByUser(user, pageable);

        return new PageResponse<>(page.map(RecentSearchResponse::from));
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
     * 최근 검색어 저장 (upsert)
     * - 이미 존재하는 검색어라면 시간만 최신으로 갱신
     * - 없으면 새로 생성
     */
    @Transactional
    public void saveRecentSearch(String username, String keyword) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 이미 존재하는지 확인 (Optional)
        recentSearchRepository.findByUserAndTerm(user, keyword)
            .ifPresentOrElse(
                // 1. 있으면 시간만 업데이트 (현재 시간 기준)
                existingSearch -> existingSearch.updateTimestamp(null),
                // 2. 없으면 새로 생성 (현재 시간 기준)
                () -> recentSearchRepository.save(RecentSearch.create(user, keyword, null))
            );

        // (선택) 최대 10개까지만 유지하고 싶다면, 오래된 것 삭제 로직 추가
    }

    /**
     * 검색어 목록 일괄 저장 (동기화)
     * - 요청받은 createdAt 시간으로 설정
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

            recentSearchRepository.findByUserAndTerm(user, keyword)
                    .ifPresentOrElse(
                            existing -> existing.updateTimestamp(createdAt),
                            () -> recentSearchRepository.save(RecentSearch.create(user, keyword, createdAt))
                    );
        }
    }
}
