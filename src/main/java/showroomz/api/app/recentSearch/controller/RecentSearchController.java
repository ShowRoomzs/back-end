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
     * 최근 검색어 저장 (단독 호출용)
     */
    @Override
    @PostMapping
    public ResponseEntity<Void> saveRecentSearch(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam("keyword") String keyword) {
        
        // 검색어가 비어있지 않을 때만 저장
        if (keyword != null && !keyword.isBlank()) {
            recentSearchService.saveRecentSearch(userPrincipal.getUsername(), keyword);
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
