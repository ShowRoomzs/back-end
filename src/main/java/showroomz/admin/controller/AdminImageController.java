package showroomz.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import showroomz.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.image.DTO.ImageUploadResponse;
import showroomz.image.service.ImageService;
import showroomz.image.type.ImageType;
import showroomz.swaggerDocs.AdminImageControllerDocs;

@RestController
@RequestMapping("/v1/admin/images")
@RequiredArgsConstructor
public class AdminImageController implements AdminImageControllerDocs {

    private final ImageService imageService;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> uploadMarketImage(
            @RequestParam("type") String typeParam,
            @RequestParam("file") MultipartFile file) {

        // 1. 이미지 타입 검증 (MARKET, PRODUCT 등 관리자 전용 타입만 허용)
        ImageType imageType;
        try {
            imageType = ImageType.valueOf(typeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
        }

        if (imageType != ImageType.MARKET && imageType != ImageType.PRODUCT) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
        }

        // 2. 업로드 (Service는 기존 로직 재사용)
        ImageUploadResponse response = imageService.uploadImage(file, imageType);
        return ResponseEntity.ok(response);
    }
}

