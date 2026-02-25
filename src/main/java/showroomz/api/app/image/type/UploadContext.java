package showroomz.api.app.image.type;

/**
 * 이미지 업로드 주체별 S3 폴더 구분 (어드민 / 유저 / 셀러)
 */
public enum UploadContext {
    USER,   // uploads/user/
    SELLER, // uploads/seller/
    ADMIN   // uploads/admin/
}
