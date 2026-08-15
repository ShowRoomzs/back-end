package showroomz.api.app.showroom.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.showroom.DTO.ShowroomVisitRequest;
import showroomz.api.app.showroom.docs.ShowroomVisitControllerDocs;
import showroomz.api.app.showroom.service.ShowroomVisitService;

/**
 * §22-4 쇼룸 방문 기록 — 쇼룸 현황 지표의 원천이다.
 *
 * <p>비로그인 방문도 쇼룸 도달에 포함되므로 이 엔드포인트는 인증을 요구하지 않는다.
 * 토큰이 실려 오면 그 사람의 방문으로, 없으면 디바이스 식별자로 센다.
 */
@RestController
@RequestMapping("/v1/user/showrooms")
@RequiredArgsConstructor
public class ShowroomVisitController implements ShowroomVisitControllerDocs {

    private final ShowroomVisitService showroomVisitService;

    @Override
    @PostMapping("/{showroomId}/visits")
    public ResponseEntity<Void> recordVisit(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("showroomId") Long showroomId,
            @RequestBody ShowroomVisitRequest request) {

        String username = userPrincipal == null ? null : userPrincipal.getUsername();
        showroomVisitService.recordVisit(username, showroomId, request);
        return ResponseEntity.noContent().build();
    }
}
