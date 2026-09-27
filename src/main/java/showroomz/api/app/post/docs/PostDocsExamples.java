package showroomz.api.app.post.docs;

/**
 * 소비자 게시물 Swagger 응답 예제 — {@link PostControllerDocs} · {@link UserFeedControllerDocs}가 함께 쓴다.
 * 어노테이션 값이라 컴파일 타임 상수(텍스트 블록 연결)로만 만든다.
 *
 * <p>기준 시각은 2026-09-26(KST)이다. 쇼룸 「제니의 뷰티룸」(5)에 공구 두 건이 있다 — 진행 중인 공구 77(게시물 456 · 09.29 종료 ·
 * D-3)과 09.24에 끝난 공구 64(게시물 389 · 종료 3일 이내라 아직 열린다). 일반 게시물은 123이다.
 */
final class PostDocsExamples {

    private PostDocsExamples() {
    }

    // ── 공구 블록 조각 (groupBuy 값) ─────────────────────────────────────────

    private static final String GB77_HEAD = """
            {
              "groupBuyId": 77,
              "title": "여름 끝 무너진 장벽, 3주면 돌아옵니다",
            """;

    private static final String GB77_META = """
              "endAt": "2026-09-29T23:59:59",
              "adDisclosure": {"label": "유료 광고 포함", "text": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다"},
              "productCount": 2,
            """;

    /** 진행 중 · 전 상품 판매 중 */
    private static final String GB77_ON_SALE = GB77_HEAD + """
              "saleState": "ON_SALE",
              "dDay": 3,
            """ + GB77_META + """
              "products": [
                {"productId": 901, "name": "시카 리페어 앰플 30ml 리필 2개 세트 기획", "thumbnailUrl": "https://cdn.example.com/products/901.jpg", "regularPrice": 38000, "groupBuyPrice": 24900, "discountRate": 34, "state": "ON_SALE", "detailAvailable": true},
                {"productId": 902, "name": "시카 토너 200ml", "thumbnailUrl": "https://cdn.example.com/products/902.jpg", "regularPrice": 22000, "groupBuyPrice": 15900, "discountRate": 28, "state": "ON_SALE", "detailAvailable": true}
              ]
            }
            """;

    /** 진행 중 · 일부 품절 — 배지는 D-day 그대로, 품절 행만 흑백 */
    private static final String GB77_PARTIALLY_SOLD_OUT = GB77_HEAD + """
              "saleState": "PARTIALLY_SOLD_OUT",
              "dDay": 3,
            """ + GB77_META + """
              "products": [
                {"productId": 901, "name": "시카 리페어 앰플 30ml 리필 2개 세트 기획", "thumbnailUrl": "https://cdn.example.com/products/901.jpg", "regularPrice": 38000, "groupBuyPrice": 24900, "discountRate": 34, "state": "ON_SALE", "detailAvailable": true},
                {"productId": 902, "name": "시카 토너 200ml", "thumbnailUrl": "https://cdn.example.com/products/902.jpg", "regularPrice": 22000, "groupBuyPrice": 15900, "discountRate": 28, "state": "SOLD_OUT", "detailAvailable": true}
              ]
            }
            """;

    private static final String GB64_HEAD = """
            {
              "groupBuyId": 64,
              "title": "봄 수분 크림, 마지막 앵콜",
              "saleState": "CLOSED",
              "dDay": null,
              "endAt": "2026-09-24T23:59:59",
              "adDisclosure": {"label": "유료 광고 포함", "text": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다"},
              "productCount": 2,
            """;

    /** 마감 · 목록 — 상품 행을 싣지 않는다(productCount는 실제 개수) */
    private static final String GB64_CLOSED_LIST = GB64_HEAD + """
              "products": []
            }
            """;

    /** 마감 · 상세 — 상품 행을 전부 싣고 모두 CLOSED · detailAvailable false */
    private static final String GB64_CLOSED_DETAIL = GB64_HEAD + """
              "products": [
                {"productId": 801, "name": "글로우 수분 크림 60ml", "thumbnailUrl": "https://cdn.example.com/products/801.jpg", "regularPrice": 32000, "groupBuyPrice": 23900, "discountRate": 25, "state": "CLOSED", "detailAvailable": false},
                {"productId": 802, "name": "글로우 수분 세럼 50ml", "thumbnailUrl": "https://cdn.example.com/products/802.jpg", "regularPrice": 28000, "groupBuyPrice": 19900, "discountRate": 29, "state": "CLOSED", "detailAvailable": false}
              ]
            }
            """;

    // ── 상세 (PostDetailResponse) ───────────────────────────────────────────

    static final String DETAIL_GENERAL = """
            {
              "contentType": "GENERAL",
              "postId": 123,
              "showroomId": 5,
              "showroomName": "제니의 뷰티룸",
              "showroomImageUrl": "https://cdn.example.com/showrooms/5.jpg",
              "content": "3주 루틴 기록",
              "imageUrls": ["https://cdn.example.com/posts/123-0.jpg", "https://cdn.example.com/posts/123-1.jpg"],
              "imageCount": 2,
              "aspectRatio": 0.8000,
              "impressionCount": 532,
              "isLiked": true,
              "likeCount": 12,
              "likeLocked": false,
              "publishedAt": "2026-09-20T12:34:56",
              "modifiedAt": "2026-09-20T13:00:00",
              "groupBuy": null
            }
            """;

    static final String DETAIL_GROUP_BUY_ON_SALE = """
            {
              "contentType": "GROUP_BUY",
              "postId": 456,
              "showroomId": 5,
              "showroomName": "제니의 뷰티룸",
              "showroomImageUrl": "https://cdn.example.com/showrooms/5.jpg",
              "content": "제가 두 달 동안 쓴 시카 라인이에요. 앰플은 리필 2개 세트라 한 통 가격에 석 달 써요.",
              "imageUrls": [],
              "imageCount": 0,
              "aspectRatio": null,
              "impressionCount": 1204,
              "isLiked": false,
              "likeCount": 24,
              "likeLocked": false,
              "publishedAt": "2026-09-24T10:00:32",
              "modifiedAt": "2026-09-25T09:12:00",
              "groupBuy":
            """ + GB77_PARTIALLY_SOLD_OUT + """
            }
            """;

    static final String DETAIL_GROUP_BUY_CLOSED = """
            {
              "contentType": "GROUP_BUY",
              "postId": 389,
              "showroomId": 5,
              "showroomName": "제니의 뷰티룸",
              "showroomImageUrl": "https://cdn.example.com/showrooms/5.jpg",
              "content": "봄 내내 썼던 수분 크림 앵콜이에요. 이번이 마지막 공구예요.",
              "imageUrls": [],
              "imageCount": 0,
              "aspectRatio": null,
              "impressionCount": 3310,
              "isLiked": true,
              "likeCount": 87,
              "likeLocked": true,
              "publishedAt": "2026-09-18T10:00:41",
              "modifiedAt": "2026-09-24T23:59:59",
              "groupBuy":
            """ + GB64_CLOSED_DETAIL + """
            }
            """;

    // ── 피드 항목 조각 (FeedItemResponse) ────────────────────────────────────
    // 항목 = 머리(판별자 · 게시물 · 쇼룸) + 플래그(isFollowing · isLiked) + 몸통(나머지 · groupBuy) + 닫기

    private static final String HEAD_GENERAL = """
            {
              "contentType": "GENERAL",
              "post": {
                "postId": 123,
                "showroomId": 5,
                "showroomName": "제니의 뷰티룸",
                "showroomImageUrl": "https://cdn.example.com/showrooms/5.jpg",
            """;

    private static final String HEAD_GB77 = """
            {
              "contentType": "GROUP_BUY",
              "post": {
                "postId": 456,
                "showroomId": 5,
                "showroomName": "제니의 뷰티룸",
                "showroomImageUrl": "https://cdn.example.com/showrooms/5.jpg",
            """;

    private static final String HEAD_GB64 = """
            {
              "contentType": "GROUP_BUY",
              "post": {
                "postId": 389,
                "showroomId": 5,
                "showroomName": "제니의 뷰티룸",
                "showroomImageUrl": "https://cdn.example.com/showrooms/5.jpg",
            """;

    private static final String NOT_FOLLOWING_NOT_LIKED = """
                "isFollowing": false,
                "isLiked": false,
            """;

    private static final String FOLLOWING_NOT_LIKED = """
                "isFollowing": true,
                "isLiked": false,
            """;

    private static final String FOLLOWING_LIKED = """
                "isFollowing": true,
                "isLiked": true,
            """;

    private static final String BODY_GENERAL = """
                "hasOngoingGroupBuy": true,
                "content": "3주 루틴 기록",
                "imageUrls": ["https://cdn.example.com/posts/123-0.jpg", "https://cdn.example.com/posts/123-1.jpg"],
                "imageCount": 2,
                "aspectRatio": 0.8000,
                "impressionCount": 532,
                "likeCount": 12,
                "likeLocked": false,
                "publishedAt": "2026-09-20T12:34:56",
                "groupBuy": null
            """;

    private static final String BODY_GB77 = """
                "hasOngoingGroupBuy": true,
                "content": "제가 두 달 동안 쓴 시카 라인이에요. 앰플은 리필 2개 세트라 한 통 가격에 석 달 써요.",
                "imageUrls": [],
                "imageCount": 0,
                "aspectRatio": null,
                "impressionCount": 1204,
                "likeCount": 24,
                "likeLocked": false,
                "publishedAt": "2026-09-24T10:00:32",
                "groupBuy":
            """ + GB77_ON_SALE;

    private static final String BODY_GB64 = """
                "hasOngoingGroupBuy": true,
                "content": "봄 내내 썼던 수분 크림 앵콜이에요. 이번이 마지막 공구예요.",
                "imageUrls": [],
                "imageCount": 0,
                "aspectRatio": null,
                "impressionCount": 3310,
                "likeCount": 87,
                "likeLocked": true,
                "publishedAt": "2026-09-18T10:00:41",
                "groupBuy":
            """ + GB64_CLOSED_LIST;

    private static final String CLOSE_NEXT = """
              }
            },
            """;

    private static final String CLOSE_LAST = """
              }
            }
            """;

    private static final String PAGE_OPEN = """
            {
              "content": [
            """;

    private static final String PAGE_CLOSE_2 = """
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false}
            }
            """;

    private static final String PAGE_CLOSE_3 = """
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 3, "limit": 20, "hasNext": false}
            }
            """;

    private static final String PAGE_CLOSE_MORE = """
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 3, "totalResults": 47, "limit": 20, "hasNext": true}
            }
            """;

    // ── 목록 예제 ────────────────────────────────────────────────────────────

    static final String PAGE_EMPTY = """
            {
              "content": [],
              "pageInfo": {"currentPage": 1, "totalPages": 0, "totalResults": 0, "limit": 20, "hasNext": false}
            }
            """;

    /** 전체(내부용) · 추천 피드 — 일반 + 진행 중 공구, 미팔로우 · 비로그인 */
    static final String PAGE_GENERAL_AND_ONGOING = PAGE_OPEN
            + HEAD_GB77 + NOT_FOLLOWING_NOT_LIKED + BODY_GB77 + CLOSE_NEXT
            + HEAD_GENERAL + NOT_FOLLOWING_NOT_LIKED + BODY_GENERAL + CLOSE_LAST
            + PAGE_CLOSE_MORE;

    /** 팔로잉 피드 — 일반 + 진행 중 공구, 전부 팔로우 중 */
    static final String PAGE_FOLLOWING = PAGE_OPEN
            + HEAD_GB77 + FOLLOWING_NOT_LIKED + BODY_GB77 + CLOSE_NEXT
            + HEAD_GENERAL + FOLLOWING_LIKED + BODY_GENERAL + CLOSE_LAST
            + PAGE_CLOSE_2;

    /** C4 아래 피드 — 일반 + 마감 공구(종료 3일 이내 · products []) */
    static final String PAGE_SHOWROOM = PAGE_OPEN
            + HEAD_GENERAL + FOLLOWING_NOT_LIKED + BODY_GENERAL + CLOSE_NEXT
            + HEAD_GB64 + FOLLOWING_NOT_LIKED + BODY_GB64 + CLOSE_LAST
            + PAGE_CLOSE_2;

    /** C4 고정 섹션 — 진행 중 공구만, 배열 */
    static final String LIST_ONGOING = "[\n"
            + HEAD_GB77 + FOLLOWING_NOT_LIKED + BODY_GB77 + CLOSE_LAST
            + "]\n";

    /** C3 좋아요 · GROUP_BUY_FIRST — 진행 중 공구 먼저, 나머지(일반 · 마감 공구)는 좋아요한 시각 최신순 */
    static final String PAGE_LIKED_GROUP_BUY_FIRST = PAGE_OPEN
            + HEAD_GB77 + FOLLOWING_LIKED + BODY_GB77 + CLOSE_NEXT
            + HEAD_GENERAL + FOLLOWING_LIKED + BODY_GENERAL + CLOSE_NEXT
            + HEAD_GB64 + FOLLOWING_LIKED + BODY_GB64 + CLOSE_LAST
            + PAGE_CLOSE_3;

    // ── 에러 ─────────────────────────────────────────────────────────────────

    static final String ERR_POST_NOT_FOUND = """
            {"code": "POST_NOT_FOUND", "message": "존재하지 않는 게시글입니다."}
            """;

    static final String ERR_SHOWROOM_NOT_FOUND = """
            {"code": "SHOWROOM_NOT_FOUND", "message": "존재하지 않는 쇼룸입니다."}
            """;

    static final String ERR_USER_NOT_FOUND = """
            {"code": "USER_NOT_FOUND", "message": "존재하지 않는 회원입니다."}
            """;

    static final String ERR_INVALID_INPUT = """
            {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다."}
            """;
}
