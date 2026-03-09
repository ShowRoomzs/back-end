package showroomz.api.creator.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.creator.auth.docs.CreatorAuthControllerDocs;
import showroomz.api.seller.auth.DTO.CreatorSignUpRequest;
import showroomz.api.seller.auth.service.SellerService;

@RestController
@RequestMapping("/v1/creator/auth")
@RequiredArgsConstructor
public class CreatorAuthController implements CreatorAuthControllerDocs {

    private final SellerService sellerService;

    @Override
    @PostMapping("/signup")
    public ResponseEntity<?> signUp(@Valid @RequestBody CreatorSignUpRequest request) {
        return ResponseEntity.status(201).body(sellerService.registerCreator(request));
    }
}
