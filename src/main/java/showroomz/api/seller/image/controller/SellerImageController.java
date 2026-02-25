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
import showroomz.api.app.image.type.UploadContext;
import showroomz.api.seller.image.docs.AdminImageControllerDocs;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/seller/images")
@RequiredArgsConstructor
public class SellerImageController implements AdminImageControllerDocs {

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

        // 2. 업로드 (셀러 폴더: uploads/seller/{type}/)
        ImageUploadResponse response = imageService.uploadImage(file, imageType, UploadContext.SELLER);
        return ResponseEntity.ok(response);
    }
}

