package showroomz.domain.post.policy;

import org.springframework.stereotype.Component;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.type.PostType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 게시물 타입 → 규칙 객체를 고르는 자리. 서비스는 {@code if (postType == ...)}를 쓰지 않고 여기서 받는다.
 *
 * <p>구현체를 스프링이 주입한 목록에서 받으므로, 공구 정책이 생기면 클래스 하나를 추가하는 것만으로
 * 붙는다 — 이 클래스는 그때도 바뀌지 않는다.
 */
@Component
public class PostPolicies {

    private final Map<PostType, PostPolicy> policies = new EnumMap<>(PostType.class);

    public PostPolicies(List<PostPolicy> policyBeans) {
        for (PostPolicy policy : policyBeans) {
            policies.put(policy.supports(), policy);
        }
    }

    public PostPolicy of(PostType type) {
        PostPolicy policy = policies.get(type);
        if (policy == null) {
            // 타입은 늘었는데 규칙을 안 만든 경우다 — 조용히 일반 규칙으로 흘리면 공구가 일반처럼 동작한다
            throw new IllegalStateException("게시물 정책이 정의되지 않은 타입입니다: " + type);
        }
        return policy;
    }

    public PostPolicy of(Post post) {
        return of(post.getPostType());
    }
}
