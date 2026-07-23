package showroomz.api.seller.image.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.app.image.DTO.ImageUploadResponse;
import showroomz.api.app.image.service.ImageService;
import showroomz.api.app.image.type.ImageType;
import showroomz.api.seller.image.docs.SellerSignupDocumentControllerDocs;

@RestController
@RequestMapping("/v1/seller/auth/signup-documents")
@RequiredArgsConstructor
public class SellerSignupDocumentController implements SellerSignupDocumentControllerDocs {

    private final ImageService imageService;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> uploadSignupDocument(
            @RequestParam("file") MultipartFile file) {
        ImageUploadResponse response = imageService.uploadImage(file, ImageType.SIGNUP_DOCUMENT);
        return ResponseEntity.ok(response);
    }
}
