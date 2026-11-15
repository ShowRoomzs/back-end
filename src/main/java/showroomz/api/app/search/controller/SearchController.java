package showroomz.api.app.search.controller;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.search.docs.SearchControllerDocs;
import showroomz.api.app.search.dto.AutoCompleteResponse;
import showroomz.api.app.search.dto.ShowroomSearchItem;
import showroomz.api.app.search.service.SearchService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/v1/user/search")
@RequiredArgsConstructor
public class SearchController implements SearchControllerDocs {

    private final SearchService searchService;

    /**
     * C14 쇼룸 검색 — 쇼룸 이름과 아이디(@handle)만 검색한다.
     */
    @Override
    @GetMapping("/showrooms")
    public ResponseEntity<PageResponse<ShowroomSearchItem>> searchShowrooms(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "keyword", required = false) String keyword,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest
    ) {
        return ResponseEntity.ok(searchService.searchShowrooms(userId(userPrincipal), keyword, pagingRequest));
    }

    /**
     * 결과 없음 화면의 "이런 쇼룸은 어떠세요" 목록.
     */
    @Override
    @GetMapping("/showrooms/active")
    public ResponseEntity<List<ShowroomSearchItem>> getActiveShowrooms(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ResponseEntity.ok(searchService.getActiveShowrooms(userId(userPrincipal), size));
    }

    @Override
    @GetMapping("/autocomplete")
    public ResponseEntity<AutoCompleteResponse> getAutocomplete(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.ok(AutoCompleteResponse.builder()
                    .products(Collections.emptyList())
                    .showrooms(Collections.emptyList())
                    .build());
        }

        return ResponseEntity.ok(searchService.getAutocomplete(userId(userPrincipal), keyword.trim()));
    }

    /** 비로그인도 검색한다 — 토큰이 있으면 차단한 쇼룸을 뺀다(App Store 1.2). */
    private static Long userId(UserPrincipal userPrincipal) {
        return userPrincipal == null ? null : userPrincipal.getUserId();
    }
}
