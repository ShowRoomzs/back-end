package showroomz.api.app.showroom.controller;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.showroom.DTO.BlockedShowroomResponse;
import showroomz.api.app.showroom.docs.ShowroomBlockControllerDocs;
import showroomz.api.app.showroom.service.ShowroomBlockService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/user/showrooms")
@RequiredArgsConstructor
public class ShowroomBlockController implements ShowroomBlockControllerDocs {

    private final ShowroomBlockService showroomBlockService;

    @Override
    @PostMapping("/{showroomId}/block")
    public ResponseEntity<Void> blockShowroom(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                              @PathVariable("showroomId") Long showroomId) {
        showroomBlockService.blockShowroom(getUsername(userPrincipal), showroomId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{showroomId}/block")
    public ResponseEntity<Void> unblockShowroom(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                @PathVariable("showroomId") Long showroomId) {
        showroomBlockService.unblockShowroom(getUsername(userPrincipal), showroomId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/blocked")
    public ResponseEntity<PageResponse<BlockedShowroomResponse>> getBlockedShowrooms(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(showroomBlockService.getBlockedShowrooms(getUsername(userPrincipal), pagingRequest));
    }

    private String getUsername(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
