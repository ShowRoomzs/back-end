package showroomz.api.creator.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.auth.DTO.CreatorApplicationRequest;
import showroomz.api.creator.auth.docs.CreatorApplicationControllerDocs;
import showroomz.api.creator.auth.service.CreatorApplicationService;

@RestController
@RequestMapping("/v1/creator/application")
@RequiredArgsConstructor
public class CreatorApplicationController implements CreatorApplicationControllerDocs {

    private final CreatorApplicationService creatorApplicationService;

    @Override
    @PostMapping
    public ResponseEntity<Void> applyForCreator(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreatorApplicationRequest request) {

        creatorApplicationService.apply(userPrincipal.getUserId(), request);
        return ResponseEntity.ok().build();
    }
}
