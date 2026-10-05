package showroomz.api.admin.thread.docs;

/**
 * 어드민 소통 스레드 Swagger 예시 JSON. 값은 한 채널의 흐름으로 이어진다 —
 * 브랜드 「무드코스메틱」(스레드 55 · BRD-1017) · 인플루언서 「뷰티_소연」(스레드 71 · INF-3021).
 */
final class AdminThreadDocsExamples {

    private AdminThreadDocsExamples() {
    }

    // ── 채널 목록 ─────────────────────────────────────────────────────────────

    static final String LIST_BRAND = """
            {
              "content": [
                {
                  "threadId": 55, "tab": "BRAND", "name": "무드코스메틱",
                  "imageUrl": "https://cdn.example.com/markets/1017/logo.png",
                  "memberNo": "BRD-1017", "memberId": 1017,
                  "managerName": "이현", "businessType": null, "memberStatus": "ACTIVE",
                  "lastMessagePreview": "여름 수분 세럼 계약 관련해 문의드립니다.",
                  "lastMessageByOperator": false, "lastMessageAt": "2026-08-14T10:05:00",
                  "unreadCount": 2, "writable": true
                },
                {
                  "threadId": 58, "tab": "BRAND", "name": "글로우랩",
                  "imageUrl": null,
                  "memberNo": "BRD-1022", "memberId": 1022,
                  "managerName": "박서윤", "businessType": null, "memberStatus": "SUSPENDED",
                  "lastMessagePreview": "계약 직권 취소 처리됨",
                  "lastMessageByOperator": true, "lastMessageAt": "2026-08-13T17:40:00",
                  "unreadCount": 0, "writable": true
                },
                {
                  "threadId": 61, "tab": "BRAND", "name": "오브제뷰티",
                  "imageUrl": null,
                  "memberNo": "BRD-1031", "memberId": 1031,
                  "managerName": "최민재", "businessType": null, "memberStatus": "ACTIVE",
                  "lastMessagePreview": null,
                  "lastMessageByOperator": false, "lastMessageAt": null,
                  "unreadCount": 0, "writable": true
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 3, "limit": 20, "hasNext": false}
            }
            """;

    static final String LIST_INFLUENCER = """
            {
              "content": [
                {
                  "threadId": 71, "tab": "INFLUENCER", "name": "뷰티_소연",
                  "imageUrl": "https://cdn.example.com/profiles/3021.jpg",
                  "memberNo": "INF-3021", "memberId": 3021,
                  "managerName": null, "businessType": "BUSINESS", "memberStatus": "ACTIVE",
                  "lastMessagePreview": "요청 · 서명 안내 다시 받기",
                  "lastMessageByOperator": false, "lastMessageAt": "2026-08-14T08:50:00",
                  "unreadCount": 1, "writable": true
                },
                {
                  "threadId": 74, "tab": "INFLUENCER", "name": "데일리_하나",
                  "imageUrl": null,
                  "memberNo": "INF-3040", "memberId": 3040,
                  "managerName": null, "businessType": "INDIVIDUAL", "memberStatus": "WITHDRAWN",
                  "lastMessagePreview": "확인했습니다. 감사합니다!",
                  "lastMessageByOperator": false, "lastMessageAt": "2026-07-30T14:12:00",
                  "unreadCount": 0, "writable": false
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false}
            }
            """;

    static final String LIST_EMPTY = """
            {"content": [], "pageInfo": {"currentPage": 1, "totalPages": 0, "totalResults": 0, "limit": 20, "hasNext": false}}
            """;

    // ── 탭 배지 ──────────────────────────────────────────────────────────────

    static final String SUMMARY = """
            {
              "brand": {"unreadCount": 3, "pendingCardCount": 0},
              "influencer": {"unreadCount": 2, "pendingCardCount": 1},
              "issue": null
            }
            """;

    static final String SUMMARY_EMPTY = """
            {
              "brand": {"unreadCount": 0, "pendingCardCount": 0},
              "influencer": {"unreadCount": 0, "pendingCardCount": 0},
              "issue": null
            }
            """;

    // ── 헤더 · 정보 바 ────────────────────────────────────────────────────────

    static final String INFO_BRAND = """
            {
              "threadId": 55, "tab": "BRAND", "name": "무드코스메틱",
              "imageUrl": "https://cdn.example.com/markets/1017/logo.png",
              "memberNo": "BRD-1017", "memberId": 1017,
              "memberStatus": "ACTIVE", "writable": true,
              "profile": {
                "managerName": "이현", "managerContact": "010-1234-5678",
                "businessType": null, "taxType": null, "businessEmail": null, "instagramUrl": null,
                "joinedAt": "2026-03-02T11:20:00"
              },
              "progress": {
                "contractSigning": 2, "contractConcluded": 5,
                "groupBuyOngoing": 1, "groupBuyEnded": null,
                "unsettledCount": null, "connectedBrandCount": null
              },
              "openIssueThreads": [
                {"threadId": 210, "kind": "GROUP_BUY_FULFILLMENT", "groupBuyId": 33, "groupBuyTitle": "봄 톤업 선크림 공구"}
              ]
            }
            """;

    static final String INFO_INFLUENCER = """
            {
              "threadId": 71, "tab": "INFLUENCER", "name": "뷰티_소연",
              "imageUrl": "https://cdn.example.com/profiles/3021.jpg",
              "memberNo": "INF-3021", "memberId": 3021,
              "memberStatus": "ACTIVE", "writable": true,
              "profile": {
                "managerName": null, "managerContact": null,
                "businessType": "BUSINESS", "taxType": null,
                "businessEmail": "soyeon.biz@example.com",
                "instagramUrl": "https://instagram.com/beauty_soyeon",
                "joinedAt": "2026-04-18T09:05:00"
              },
              "progress": {
                "contractSigning": 1, "contractConcluded": 3,
                "groupBuyOngoing": 1, "groupBuyEnded": 2,
                "unsettledCount": null, "connectedBrandCount": 4
              },
              "openIssueThreads": []
            }
            """;

    static final String INFO_WITHDRAWN = """
            {
              "threadId": 74, "tab": "INFLUENCER", "name": "데일리_하나",
              "imageUrl": null,
              "memberNo": "INF-3040", "memberId": 3040,
              "memberStatus": "WITHDRAWN", "writable": false,
              "profile": {
                "managerName": null, "managerContact": null,
                "businessType": "INDIVIDUAL", "taxType": null,
                "businessEmail": null, "instagramUrl": null,
                "joinedAt": "2026-05-10T15:30:00"
              },
              "progress": {
                "contractSigning": 0, "contractConcluded": 1,
                "groupBuyOngoing": 0, "groupBuyEnded": 1,
                "unsettledCount": null, "connectedBrandCount": 0
              },
              "openIssueThreads": []
            }
            """;

    // ── 메시지 ───────────────────────────────────────────────────────────────

    /** 인플루언서 채널 첫 페이지 — 최신순. 재발송 요청 카드가 아직 PENDING이다. */
    static final String MESSAGES_PENDING_CARD = """
            {
              "content": [
                {
                  "messageId": 9012, "messageType": "SYSTEM", "senderType": "CREATOR",
                  "mine": false, "senderName": "뷰티_소연", "operatorName": null, "autoNotice": false,
                  "content": "요청 · 서명 안내 다시 받기", "attachments": [],
                  "card": {
                    "cardType": "CONTRACT_RESEND_REQUEST", "title": "요청 · 서명 안내 다시 받기", "tone": "WARNING",
                    "contractId": 41, "contractNumber": "CTR-20260813-041", "groupBuyTitle": "겨울 리페어 크림 공구",
                    "detail": {"requesterType": "CREATOR", "requesterName": "뷰티_소연", "requestedAt": "2026-08-14T08:50:00"},
                    "action": {"type": "RESEND_NOTICE", "state": "PENDING", "canExecute": true,
                               "doneAt": null, "doneByName": null, "noticeMessageId": null}
                  },
                  "createdAt": "2026-08-14T08:50:00"
                },
                {
                  "messageId": 9008, "messageType": "TEXT", "senderType": "CREATOR",
                  "mine": false, "senderName": "뷰티_소연", "operatorName": null, "autoNotice": false,
                  "content": "촬영본 먼저 공유드려요.",
                  "attachments": [
                    {"attachmentId": 501, "status": "UPLOADED", "attachmentType": "VIDEO",
                     "fileUrl": "https://cdn.example.com/uploads/message/71/9f1c.mp4", "originalName": "촬영본.mp4",
                     "extension": "mp4", "sizeBytes": 31457280, "durationSeconds": 58, "sortOrder": 0},
                    {"attachmentId": 502, "status": "UPLOADED", "attachmentType": "DOCUMENT",
                     "fileUrl": "https://cdn.example.com/uploads/message/71/a7d2.pdf", "originalName": "콘티.pdf",
                     "extension": "pdf", "sizeBytes": 1048576, "durationSeconds": null, "sortOrder": 1}
                  ],
                  "card": null, "createdAt": "2026-08-13T16:20:00"
                },
                {
                  "messageId": 9005, "messageType": "TEXT", "senderType": "ADMIN",
                  "mine": true, "senderName": null, "operatorName": "김운영", "autoNotice": false,
                  "content": "안녕하세요, 공구 일정 확인 부탁드립니다.", "attachments": [],
                  "card": null, "createdAt": "2026-08-13T10:00:00"
                },
                {
                  "messageId": 8800, "messageType": "TEXT", "senderType": "ADMIN",
                  "mine": true, "senderName": null, "operatorName": null, "autoNotice": true,
                  "content": "안녕하세요, 뷰티_소연님. 아직 연결된 브랜드가 없네요. 브랜드가 연결 요청을 보내면 [요청함] 탭에서 확인하실 수 있어요.",
                  "attachments": [], "card": null, "createdAt": "2026-04-18T09:05:01"
                }
              ],
              "nextCursor": null,
              "hasNext": false
            }
            """;

    /** 재발송 완료 알림을 보낸 뒤 — 카드는 DONE으로 굳고 위에 자동 안내 말풍선이 붙는다. */
    static final String MESSAGES_DONE_CARD = """
            {
              "content": [
                {
                  "messageId": 9013, "messageType": "TEXT", "senderType": "ADMIN",
                  "mine": true, "senderName": null, "operatorName": "김운영", "autoNotice": true,
                  "content": "모두싸인에서 서명 안내를 다시 보내드렸습니다. 메일함(스팸함 포함)을 확인해 주세요.",
                  "attachments": [], "card": null, "createdAt": "2026-08-14T08:55:00"
                },
                {
                  "messageId": 9012, "messageType": "SYSTEM", "senderType": "CREATOR",
                  "mine": false, "senderName": "뷰티_소연", "operatorName": null, "autoNotice": false,
                  "content": "요청 · 서명 안내 다시 받기", "attachments": [],
                  "card": {
                    "cardType": "CONTRACT_RESEND_REQUEST", "title": "요청 · 서명 안내 다시 받기", "tone": "NEUTRAL",
                    "contractId": 41, "contractNumber": "CTR-20260813-041", "groupBuyTitle": "겨울 리페어 크림 공구",
                    "detail": {"requesterType": "CREATOR", "requesterName": "뷰티_소연", "requestedAt": "2026-08-14T08:50:00"},
                    "action": {"type": "RESEND_NOTICE", "state": "DONE", "canExecute": false,
                               "doneAt": "2026-08-14T08:55:00", "doneByName": "김운영", "noticeMessageId": 9013}
                  },
                  "createdAt": "2026-08-14T08:50:00"
                }
              ],
              "nextCursor": 9012,
              "hasNext": true
            }
            """;

    /** 브랜드 채널 — 알림 전에 계약이 취소돼 닫힌 요청 카드(CLOSED)와 직권 취소 결과 카드. */
    static final String MESSAGES_CLOSED_AND_CANCELED = """
            {
              "content": [
                {
                  "messageId": 9101, "messageType": "SYSTEM", "senderType": "ADMIN",
                  "mine": false, "senderName": null, "operatorName": null, "autoNotice": false,
                  "content": "계약 직권 취소 처리됨", "attachments": [],
                  "card": {
                    "cardType": "CONTRACT_ADMIN_CANCELED", "title": "계약 직권 취소 처리됨", "tone": "NEUTRAL",
                    "contractId": 44, "contractNumber": "CTR-20260810-044", "groupBuyTitle": "여름 수분 세럼 공구",
                    "detail": {"reasonLabel": "상품 재고 부족 — 브랜드 측 생산 일정 지연", "processedAt": "2026-08-13T17:40:00",
                               "processedByName": "김운영", "notifiedBothParties": true},
                    "action": null
                  },
                  "createdAt": "2026-08-13T17:40:00"
                },
                {
                  "messageId": 9090, "messageType": "SYSTEM", "senderType": "SELLER",
                  "mine": false, "senderName": "무드코스메틱", "operatorName": null, "autoNotice": false,
                  "content": "요청 · 서명 안내 다시 받기", "attachments": [],
                  "card": {
                    "cardType": "CONTRACT_RESEND_REQUEST", "title": "요청 · 서명 안내 다시 받기", "tone": "NEUTRAL",
                    "contractId": 44, "contractNumber": "CTR-20260810-044", "groupBuyTitle": "여름 수분 세럼 공구",
                    "detail": {"requesterType": "SELLER", "requesterName": "무드코스메틱", "requestedAt": "2026-08-13T09:12:00"},
                    "action": {"type": "RESEND_NOTICE", "state": "CLOSED", "canExecute": false,
                               "doneAt": null, "doneByName": null, "noticeMessageId": null}
                  },
                  "createdAt": "2026-08-13T09:12:00"
                }
              ],
              "nextCursor": null,
              "hasNext": false
            }
            """;

    static final String MESSAGES_EMPTY = """
            {"content": [], "nextCursor": null, "hasNext": false}
            """;

    // ── 전송 ─────────────────────────────────────────────────────────────────

    static final String REQ_SEND_TEXT = """
            {"clientMessageId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "content": "확인했습니다. 오늘 중으로 답변드릴게요."}
            """;

    static final String REQ_SEND_WITH_ATTACHMENTS = """
            {"clientMessageId": "0f9e8d7c-6b5a-4321-9876-fedcba012345", "content": "수정본 첨부드립니다.", "attachmentIds": [601, 602]}
            """;

    static final String REQ_SEND_ATTACHMENTS_ONLY = """
            {"clientMessageId": "5c4b3a29-1807-4f6e-8d5c-4b3a29180716", "attachmentIds": [603]}
            """;

    static final String SEND_CREATED = """
            {
              "messageId": 9020, "messageType": "TEXT", "senderType": "ADMIN",
              "mine": true, "senderName": null, "operatorName": "김운영", "autoNotice": false,
              "content": "수정본 첨부드립니다.",
              "attachments": [
                {"attachmentId": 601, "status": "UPLOADED", "attachmentType": "DOCUMENT",
                 "fileUrl": "https://cdn.example.com/uploads/message/55/c1e4.pdf", "originalName": "계약 안내.pdf",
                 "extension": "pdf", "sizeBytes": 524288, "durationSeconds": null, "sortOrder": 0},
                {"attachmentId": 602, "status": "UPLOADED", "attachmentType": "IMAGE",
                 "fileUrl": "https://cdn.example.com/uploads/message/55/d2f5.png", "originalName": "화면캡처.png",
                 "extension": "png", "sizeBytes": 204800, "durationSeconds": null, "sortOrder": 1}
              ],
              "card": null, "createdAt": "2026-08-14T11:02:00"
            }
            """;

    // ── 첨부 ─────────────────────────────────────────────────────────────────

    static final String REQ_PRESIGN = """
            {"fileName": "계약 안내.pdf", "contentType": "application/pdf", "sizeBytes": 524288}
            """;

    static final String PRESIGN_CREATED = """
            {
              "attachmentId": 601,
              "uploadUrl": "https://bucket.s3.ap-northeast-2.amazonaws.com/uploads/message/55/c1e4.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=900&...",
              "requiredContentType": "application/pdf",
              "expiresInSeconds": 900
            }
            """;

    static final String REQ_COMPLETE_VIDEO = """
            {"durationSeconds": 58}
            """;

    static final String COMPLETE_UPLOADED = """
            {
              "attachmentId": 601, "status": "UPLOADED", "attachmentType": "DOCUMENT",
              "fileUrl": "https://cdn.example.com/uploads/message/55/c1e4.pdf", "originalName": "계약 안내.pdf",
              "extension": "pdf", "sizeBytes": 524288, "durationSeconds": null, "sortOrder": null
            }
            """;

    static final String COMPLETE_REJECTED = """
            {
              "attachmentId": 604, "status": "REJECTED", "attachmentType": "IMAGE",
              "fileUrl": "https://cdn.example.com/uploads/message/55/e3a6.jpg", "originalName": "제품컷.jpg",
              "extension": "jpg", "sizeBytes": 2097152, "durationSeconds": null, "sortOrder": null
            }
            """;

    static final String REQ_DOWNLOAD_ALL = """
            {"attachmentIds": [501, 502]}
            """;

    static final String REQ_DOWNLOAD_ONE = """
            {"attachmentIds": [502]}
            """;

    static final String DOWNLOAD_OK = """
            [
              {"attachmentId": 501,
               "downloadUrl": "https://bucket.s3.ap-northeast-2.amazonaws.com/uploads/message/71/9f1c.mp4?response-content-disposition=attachment...&X-Amz-Expires=300&...",
               "originalName": "촬영본.mp4", "sizeBytes": 31457280, "expiresInSeconds": 300},
              {"attachmentId": 502,
               "downloadUrl": "https://bucket.s3.ap-northeast-2.amazonaws.com/uploads/message/71/a7d2.pdf?response-content-disposition=attachment...&X-Amz-Expires=300&...",
               "originalName": "콘티.pdf", "sizeBytes": 1048576, "expiresInSeconds": 300}
            ]
            """;

    // ── 재발송 완료 알림 ──────────────────────────────────────────────────────

    static final String RESEND_NOTICE_SENT = """
            {
              "alreadyNotified": false,
              "card": {
                "messageId": 9012, "messageType": "SYSTEM", "senderType": "CREATOR",
                "mine": false, "senderName": "뷰티_소연", "operatorName": null, "autoNotice": false,
                "content": "요청 · 서명 안내 다시 받기", "attachments": [],
                "card": {
                  "cardType": "CONTRACT_RESEND_REQUEST", "title": "요청 · 서명 안내 다시 받기", "tone": "NEUTRAL",
                  "contractId": 41, "contractNumber": "CTR-20260813-041", "groupBuyTitle": "겨울 리페어 크림 공구",
                  "detail": {"requesterType": "CREATOR", "requesterName": "뷰티_소연", "requestedAt": "2026-08-14T08:50:00"},
                  "action": {"type": "RESEND_NOTICE", "state": "DONE", "canExecute": false,
                             "doneAt": "2026-08-14T08:55:00", "doneByName": "김운영", "noticeMessageId": 9013}
                },
                "createdAt": "2026-08-14T08:50:00"
              },
              "notice": {
                "messageId": 9013, "messageType": "TEXT", "senderType": "ADMIN",
                "mine": true, "senderName": null, "operatorName": "김운영", "autoNotice": true,
                "content": "모두싸인에서 서명 안내를 다시 보내드렸습니다. 메일함(스팸함 포함)을 확인해 주세요.",
                "attachments": [], "card": null, "createdAt": "2026-08-14T08:55:00"
              }
            }
            """;

    /** 다른 운영자가 먼저 눌렀다 — 처리자는 먼저 누른 사람(김운영)이다. */
    static final String RESEND_NOTICE_ALREADY = """
            {
              "alreadyNotified": true,
              "card": {
                "messageId": 9012, "messageType": "SYSTEM", "senderType": "CREATOR",
                "mine": false, "senderName": "뷰티_소연", "operatorName": null, "autoNotice": false,
                "content": "요청 · 서명 안내 다시 받기", "attachments": [],
                "card": {
                  "cardType": "CONTRACT_RESEND_REQUEST", "title": "요청 · 서명 안내 다시 받기", "tone": "NEUTRAL",
                  "contractId": 41, "contractNumber": "CTR-20260813-041", "groupBuyTitle": "겨울 리페어 크림 공구",
                  "detail": {"requesterType": "CREATOR", "requesterName": "뷰티_소연", "requestedAt": "2026-08-14T08:50:00"},
                  "action": {"type": "RESEND_NOTICE", "state": "DONE", "canExecute": false,
                             "doneAt": "2026-08-14T08:55:00", "doneByName": "김운영", "noticeMessageId": 9013}
                },
                "createdAt": "2026-08-14T08:50:00"
              },
              "notice": {
                "messageId": 9013, "messageType": "TEXT", "senderType": "ADMIN",
                "mine": true, "senderName": null, "operatorName": "김운영", "autoNotice": true,
                "content": "모두싸인에서 서명 안내를 다시 보내드렸습니다. 메일함(스팸함 포함)을 확인해 주세요.",
                "attachments": [], "card": null, "createdAt": "2026-08-14T08:55:00"
              }
            }
            """;

    // ── 오류 ─────────────────────────────────────────────────────────────────

    static final String ERR_INVALID_INPUT = """
            {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다."}
            """;

    static final String ERR_NOT_BLANK = """
            {"code": "INVALID_INPUT", "message": "공백일 수 없습니다"}
            """;

    static final String ERR_UNAUTHORIZED = """
            {"code": "UNAUTHORIZED", "message": "인증 정보가 유효하지 않습니다."}
            """;

    static final String ERR_THREAD_NOT_FOUND = """
            {"code": "THREAD_NOT_FOUND", "message": "존재하지 않는 스레드입니다."}
            """;

    static final String ERR_THREAD_ACCESS_DENIED = """
            {"code": "THREAD_ACCESS_DENIED", "message": "해당 스레드에 대한 권한이 없습니다."}
            """;

    static final String ERR_THREAD_READ_ONLY = """
            {"code": "THREAD_READ_ONLY", "message": "탈퇴한 회원의 채널에는 메시지를 보낼 수 없습니다."}
            """;

    static final String ERR_MESSAGE_EMPTY = """
            {"code": "MESSAGE_EMPTY", "message": "메시지 내용 또는 첨부 중 하나는 필요합니다."}
            """;

    static final String ERR_ATTACHMENT_COUNT_EXCEEDED = """
            {"code": "ATTACHMENT_COUNT_EXCEEDED", "message": "첨부는 메시지 1건당 최대 20개까지 가능합니다."}
            """;

    static final String ERR_ATTACHMENT_SIZE_EXCEEDED = """
            {"code": "ATTACHMENT_SIZE_EXCEEDED", "message": "첨부 총 용량은 메시지 1건당 500MB를 초과할 수 없습니다."}
            """;

    static final String ERR_ATTACHMENT_EXTENSION_NOT_ALLOWED = """
            {"code": "ATTACHMENT_EXTENSION_NOT_ALLOWED", "message": "허용되지 않는 파일 형식입니다."}
            """;

    static final String ERR_ATTACHMENT_NOT_UPLOADED = """
            {"code": "ATTACHMENT_NOT_UPLOADED", "message": "업로드가 완료되지 않은 첨부입니다."}
            """;

    static final String ERR_ATTACHMENT_ACCESS_DENIED = """
            {"code": "ATTACHMENT_ACCESS_DENIED", "message": "해당 첨부에 대한 권한이 없습니다."}
            """;

    static final String ERR_ATTACHMENT_ALREADY_ATTACHED = """
            {"code": "ATTACHMENT_ALREADY_ATTACHED", "message": "이미 다른 메시지에 연결된 첨부입니다."}
            """;

    static final String ERR_MESSAGE_CARD_NOT_FOUND = """
            {"code": "MESSAGE_CARD_NOT_FOUND", "message": "요청 카드를 찾을 수 없습니다."}
            """;

    static final String ERR_CONTRACT_STATUS_CONFLICT = """
            {"code": "CONTRACT_STATUS_CONFLICT", "message": "계약 상태가 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요."}
            """;
}
