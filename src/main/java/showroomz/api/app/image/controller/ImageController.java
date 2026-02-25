package showroomz.api.app.image.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import showroomz.api.app.auth.token.AuthToken;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.api.app.image.DTO.ImageUploadResponse;
import showroomz.api.app.image.docs.ImageControllerDocs;
import showroomz.api.app.image.service.ImageService;
import showroomz.api.app.image.type.ImageType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.HeaderUtil;

@RestController
@RequestMapping("/v1/user/images")
@RequiredArgsConstructor
public class ImageController implements ImageControllerDocs {

    private final ImageService imageService;
    private final AuthTokenProvider tokenProvider;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> uploadImage(
            HttpServletRequest request,
            @RequestParam("type") String typeParam,
            @RequestParam("file") MultipartFile file) {

        // 1. Authorization 헤더 검증
        String accessToken = HeaderUtil.getAccessToken(request);
        if (accessToken == null || accessToken.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }

        AuthToken authToken = tokenProvider.convertAuthToken(accessToken);
        if (!authToken.validate()) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }

        // 2. type 파라미터 검증
        ImageType imageType;
        try {
            imageType = ImageType.valueOf(typeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
        }

        // 3. 일반 유저는 PROFILE, REVIEW, INQUIRY만 가능하도록 제한
        if (imageType == ImageType.MARKET || imageType == ImageType.PRODUCT) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 4. 파일 업로드 (서비스 내부에서 BusinessException 발생 가능)
        ImageUploadResponse response = imageService.uploadImage(file, imageType);
        
        return ResponseEntity.ok(response);
    }
}
