package showroomz.api.app.home.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.home.docs.HomeControllerDocs;
import showroomz.api.app.home.dto.HomeSummaryResponse;
import showroomz.api.app.home.service.HomeSummaryService;

@RestController
@RequestMapping("/v1/user/home")
@RequiredArgsConstructor
public class HomeController implements HomeControllerDocs {

    private final HomeSummaryService homeSummaryService;

    @Override
    @GetMapping("/summary")
    public ResponseEntity<HomeSummaryResponse> getHomeSummary(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(homeSummaryService.getSummary(userPrincipal.getUsername()));
    }
}
