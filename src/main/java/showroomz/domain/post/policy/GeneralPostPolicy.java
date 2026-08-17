package showroomz.domain.post.policy;

import org.springframework.stereotype.Component;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 일반 게시물 규칙 (§24).
 */
@Component
public class GeneralPostPolicy implements PostPolicy {

    @Override
    public PostType supports() {
        return PostType.GENERAL;
    }

    /**
     * 게시하기의 활성 조건은 <b>사진 최소 1장</b>이다 (§24-3).
     *
     * <p>FE는 이 조건을 에러 문구 없이 버튼 비활성으로만 표현하지만, 서버까지 막지 않으면
     * API를 직접 호출해 빈 게시물을 만들 수 있다. FE가 이 상태에 도달할 일이 없으므로
     * 서버가 거절해도 문구 정책과 충돌하지 않는다.
     */
    @Override
    public void validateForPublish(Post post) {
        if (post.getImages().isEmpty()) {
            throw new BusinessException(ErrorCode.POST_IMAGE_REQUIRED);
        }
    }

    /** 게시 후 수정에 제한이 없다 — 공구 게시물의 노출중 잠금과 다르다 (§24-3) */
    @Override
    public void validateEditable(Post post) {
        if (!post.getStatus().isEditable()) {
            throw new BusinessException(ErrorCode.POST_NOT_EDITABLE);
        }
    }

    @Override
    public boolean canLike(Post post) {
        return post.isVisibleToConsumer();
    }

    /** 일반 게시물은 광고가 아니므로 대가관계 표시를 넣지 않는다 (§24 비교표) */
    @Override
    public boolean requiresAdDisclosure() {
        return false;
    }
}
