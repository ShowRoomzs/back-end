package showroomz.api.app.search.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.app.search.docs.SearchControllerDocs;
import showroomz.api.app.search.dto.AutoCompleteResponse;
import showroomz.api.app.search.service.SearchService;

import java.util.Collections;

@RestController
@RequestMapping("/v1/user/search")
@RequiredArgsConstructor
public class SearchController implements SearchControllerDocs {

    private final SearchService searchService;

    @Override
    @GetMapping("/autocomplete")
    public ResponseEntity<AutoCompleteResponse> getAutocomplete(
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.ok(AutoCompleteResponse.builder()
                    .products(Collections.emptyList())
                    .markets(Collections.emptyList())
                    .showrooms(Collections.emptyList())
                    .build());
        }

        return ResponseEntity.ok(searchService.getAutocomplete(keyword));
    }
}
