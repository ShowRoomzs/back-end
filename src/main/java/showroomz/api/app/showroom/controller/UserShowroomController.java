package showroomz.api.app.showroom.controller;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.showroom.DTO.ShowroomDetailResponse;
import showroomz.api.app.showroom.DTO.ShowroomListItem;
import showroomz.api.app.showroom.docs.UserShowroomControllerDocs;
import showroomz.api.app.showroom.service.UserShowroomService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

/**
 * 소비자 쇼룸 조회 — 구 샵 API({@code /v1/user/shops})가 옮겨 온 자리다.
 *
 * <p>경로가 {@code /shops}에서 {@code /showrooms}로 바뀐 것은 이름만 다듬은 것이 아니다. 조회
 * 대상이 마켓에서 쇼룸으로 바뀌었고, 그 결과 이 경로 아래의 팔로우·방문·게시물 API와 같은 ID
 * 공간(쇼룸 ID)을 쓰게 됐다. 예전에는 샵 ID(마켓 ID)와 쇼룸 ID가 서로 다른 값이었다.
 *
 * <p>비로그인도 열 수 있다 — 앱을 처음 켠 사람이 로그인 벽 앞에서 쇼룸을 하나도 못 보면 가입할
 * 이유가 생기지 않는다. 토큰이 실려 오면 {@code isFollowing}이 채워진다.
 */
@RestController
@RequestMapping("/v1/user/showrooms")
@RequiredArgsConstructor
public class UserShowroomController implements UserShowroomControllerDocs {

    private final UserShowroomService userShowroomService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<ShowroomListItem>> getShowrooms(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(name = "keyword", required = false) String keyword,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(
                userShowroomService.getShowrooms(usernameOrNull(userPrincipal), keyword, pagingRequest));
    }

    @Override
    @GetMapping("/{showroomId}")
    public ResponseEntity<ShowroomDetailResponse> getShowroom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("showroomId") Long showroomId) {
        return ResponseEntity.ok(
                userShowroomService.getShowroom(usernameOrNull(userPrincipal), showroomId));
    }

    private static String usernameOrNull(UserPrincipal userPrincipal) {
        return userPrincipal != null ? userPrincipal.getUsername() : null;
    }
}
