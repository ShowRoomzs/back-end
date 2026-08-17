package showroomz.api.app.post.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.post.DTO.PostImpressionRequest;
import showroomz.api.app.post.docs.UserPostImpressionControllerDocs;
import showroomz.api.app.post.service.PostImpressionService;

/**
 * 게시물 노출 적재 (§24-7).
 *
 * <p>경로가 게시물 하나에 달려 있지 않은 이유 — 노출은 <b>배열로 묶어</b> 보낸다. 카드 한 장마다
 * 요청하면 피드 스크롤에서 요청이 폭발한다. 게시물별 경로를 두면 배치가 불가능하다.
 *
 * <p>비로그인 조회도 노출에 포함되므로 인증을 요구하지 않는다. 토큰이 실려 오면 필터가 인증을
 * 채워 주므로 로그인 조회는 사용자 기준으로 집계되고, 연령·성별 표본이 된다.
 */
@RestController
@RequestMapping("/v1/user/posts")
@RequiredArgsConstructor
public class UserPostImpressionController implements UserPostImpressionControllerDocs {

    private final PostImpressionService postImpressionService;

    @Override
    @PostMapping("/impressions")
    public ResponseEntity<Void> recordImpressions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PostImpressionRequest request) {
        String username = userPrincipal == null ? null : userPrincipal.getUsername();
        postImpressionService.recordImpressions(username, request);
        return ResponseEntity.noContent().build();
    }
}
