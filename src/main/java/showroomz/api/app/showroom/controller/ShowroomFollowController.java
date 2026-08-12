package showroomz.api.app.showroom.controller;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.showroom.DTO.FollowingShowroomResponse;
import showroomz.api.app.showroom.docs.ShowroomFollowControllerDocs;
import showroomz.api.app.showroom.service.ShowroomFollowService;
import showroomz.api.app.showroom.type.FollowingShowroomSort;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/user/showrooms")
@RequiredArgsConstructor
public class ShowroomFollowController implements ShowroomFollowControllerDocs {

    private final ShowroomFollowService showroomFollowService;

    // 쇼룸 팔로우 - 성공 시 204 No Content
    @Override
    @PostMapping("/{showroomId}/follow")
    public ResponseEntity<Void> followShowroom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("showroomId") Long showroomId) {

        showroomFollowService.followShowroom(getUsername(userPrincipal), showroomId);
        return ResponseEntity.noContent().build();
    }

    // 쇼룸 팔로우 취소 - 성공 시 204 No Content
    @Override
    @DeleteMapping("/{showroomId}/follow")
    public ResponseEntity<Void> unfollowShowroom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("showroomId") Long showroomId) {

        showroomFollowService.unfollowShowroom(getUsername(userPrincipal), showroomId);
        return ResponseEntity.noContent().build();
    }

    // 팔로우한 쇼룸 목록 조회
    @Override
    @GetMapping("/following")
    public ResponseEntity<PageResponse<FollowingShowroomResponse>> getFollowedShowrooms(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(name = "sort", required = false, defaultValue = "DEFAULT") FollowingShowroomSort sort,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {

        return ResponseEntity.ok(
                showroomFollowService.getFollowedShowrooms(getUsername(userPrincipal), sort, pagingRequest));
    }

    private String getUsername(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
