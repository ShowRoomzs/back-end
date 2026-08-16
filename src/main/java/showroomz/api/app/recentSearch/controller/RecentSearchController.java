package showroomz.api.app.recentSearch.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.recentSearch.DTO.RecentSearchResponse;
import showroomz.api.app.recentSearch.DTO.RecentSearchSyncRequest;
import showroomz.api.app.recentSearch.docs.RecentSearchControllerDocs;
import showroomz.api.app.recentSearch.service.RecentSearchService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/user/recent-searches")
@RequiredArgsConstructor
public class RecentSearchController implements RecentSearchControllerDocs {

    private final RecentSearchService recentSearchService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<RecentSearchResponse>> getMyRecentSearches(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @ModelAttribute PagingRequest pagingRequest
    ) {
        return ResponseEntity.ok(
            recentSearchService.getMyRecentSearches(userPrincipal.getUsername(), pagingRequest)
        );
    }

    @Override
    @DeleteMapping("/{recentSearchId}")
    public ResponseEntity<Void> deleteRecentSearch(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("recentSearchId") Long recentSearchId
    ) {
        recentSearchService.deleteRecentSearch(userPrincipal.getUsername(), recentSearchId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 최근 검색 전체 삭제 — 목록 상단의 [전체 삭제]
     */
    @Override
    @DeleteMapping
    public ResponseEntity<Void> deleteAllRecentSearches(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        recentSearchService.deleteAllRecentSearches(userPrincipal.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * 최근 검색 저장 (단독 호출용)
     * - `keyword`만 보내면 검색어(TERM) 행, `showroomId`만 보내면 쇼룸(SHOWROOM) 행이 쌓인다.
     */
    @Override
    @PostMapping
    public ResponseEntity<Void> saveRecentSearch(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "showroomId", required = false) Long showroomId) {

        // 검색 결과에서 쇼룸으로 들어간 경우 — 쇼룸 행으로 남긴다
        if (showroomId != null) {
            recentSearchService.saveRecentShowroom(userPrincipal.getUsername(), showroomId);
            return ResponseEntity.noContent().build();
        }

        // 검색어가 비어있지 않을 때만 저장
        if (keyword != null && !keyword.isBlank()) {
            recentSearchService.saveRecentSearch(userPrincipal.getUsername(), keyword.trim());
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * 로컬 검색어 목록 서버 동기화 (로그인 직후 호출)
     */
    @Override
    @PostMapping("/sync")
    public ResponseEntity<Void> syncRecentSearches(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody RecentSearchSyncRequest request
    ) {
        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            recentSearchService.syncRecentSearches(userPrincipal.getUsername(), request.getKeywords());
        }
        return ResponseEntity.noContent().build();
    }
}
