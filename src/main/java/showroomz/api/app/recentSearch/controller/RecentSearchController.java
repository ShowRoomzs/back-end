package showroomz.api.app.recentSearch.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.docs.RecentSearchControllerDocs;
import showroomz.api.app.recentSearch.DTO.RecentSearchResponse;
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
            @AuthenticationPrincipal User principal,
            @ModelAttribute PagingRequest pagingRequest
    ) {
        return ResponseEntity.ok(
            recentSearchService.getMyRecentSearches(principal.getUsername(), pagingRequest)
        );
    }

    @Override
    @DeleteMapping("/{recentSearchId}")
    public ResponseEntity<Void> deleteRecentSearch(
            @AuthenticationPrincipal User principal,
            @PathVariable Long recentSearchId
    ) {
        recentSearchService.deleteRecentSearch(principal.getUsername(), recentSearchId);
        return ResponseEntity.noContent().build();
    }
}
