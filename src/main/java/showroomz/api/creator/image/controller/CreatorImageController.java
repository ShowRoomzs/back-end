package showroomz.api.creator.image.controller;

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
import showroomz.api.creator.image.docs.CreatorImageControllerDocs;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/creator/images")
@RequiredArgsConstructor
public class CreatorImageController implements CreatorImageControllerDocs {

    private final ImageService imageService;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> uploadImage(
            @RequestParam("type") String typeParam,
            @RequestParam("file") MultipartFile file
    ) {
        ImageType imageType;
        try {
            imageType = ImageType.valueOf(typeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
        }

        // 크리에이터 업로드 허용 타입: POST(게시글), PRODUCT(상품), MARKET(마켓 대표)
        if (imageType != ImageType.POST && imageType != ImageType.PRODUCT && imageType != ImageType.MARKET) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
        }

        ImageUploadResponse response = imageService.uploadImage(file, imageType);
        return ResponseEntity.ok(response);
    }
}

