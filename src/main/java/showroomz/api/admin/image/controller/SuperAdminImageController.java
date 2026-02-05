package showroomz.api.admin.image.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.admin.docs.SuperAdminImageControllerDocs;
import showroomz.api.app.image.DTO.ImageUploadResponse;
import showroomz.api.app.image.service.ImageService;
import showroomz.api.app.image.type.ImageType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/admin/images")
@RequiredArgsConstructor
public class SuperAdminImageController implements SuperAdminImageControllerDocs {

    private final ImageService imageService;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> uploadCategoryImage(
            @RequestParam("type") String typeParam,
            @RequestParam("file") MultipartFile file) {

        ImageType imageType;
        try {
            imageType = ImageType.valueOf(typeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
        }

        if (imageType != ImageType.CATEGORY) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
        }

        ImageUploadResponse response = imageService.uploadImage(file, imageType);
        return ResponseEntity.ok(response);
    }
}
