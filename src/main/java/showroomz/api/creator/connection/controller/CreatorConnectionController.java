package showroomz.api.creator.connection.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.connection.dto.ConnectionCodeResponse;
import showroomz.api.creator.connection.dto.ConnectionRequestItem;
import showroomz.api.creator.connection.docs.CreatorConnectionControllerDocs;
import showroomz.api.creator.connection.service.CreatorConnectionService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/creator/connections")
@RequiredArgsConstructor
@Hidden
public class CreatorConnectionController implements CreatorConnectionControllerDocs {

    private final CreatorConnectionService creatorConnectionService;

    @Override
    @GetMapping("/requests")
    public ResponseEntity<PageResponse<ConnectionRequestItem>> getRequests(
            @ModelAttribute PagingRequest pagingRequest) {
        PageResponse<ConnectionRequestItem> response =
                creatorConnectionService.getRequests(getCurrentUserEmail(), pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{connectionId}/accept")
    public ResponseEntity<Void> accept(@PathVariable("connectionId") Long connectionId) {
        creatorConnectionService.accept(getCurrentUserEmail(), connectionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{connectionId}/reject")
    public ResponseEntity<Void> reject(@PathVariable("connectionId") Long connectionId) {
        creatorConnectionService.reject(getCurrentUserEmail(), connectionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/code")
    public ResponseEntity<ConnectionCodeResponse> getMyConnectionCode() {
        ConnectionCodeResponse response = creatorConnectionService.getMyConnectionCode(getCurrentUserEmail());
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/code/reissue")
    public ResponseEntity<ConnectionCodeResponse> reissueConnectionCode() {
        ConnectionCodeResponse response = creatorConnectionService.reissueConnectionCode(getCurrentUserEmail());
        return ResponseEntity.ok(response);
    }

    private String getCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }
}
