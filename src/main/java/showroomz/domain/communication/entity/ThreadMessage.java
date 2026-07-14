package showroomz.domain.communication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.communication.type.ThreadWriterType;

import java.util.ArrayList;
import java.util.List;

/**
 * 소통 스레드 메시지 — 게시판형(실시간 아님).
 * 미디어 첨부는 이미지·영상 합산 1글당 최대 {@link #MAX_MEDIA_COUNT}개(서비스 계층 검증).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "thread_message")
public class ThreadMessage extends BaseTimeEntity {

    public static final int MAX_MEDIA_COUNT = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private CommunicationThread thread;

    @Enumerated(EnumType.STRING)
    @Column(name = "writer_type", nullable = false, length = 20)
    private ThreadWriterType writerType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @ElementCollection
    @CollectionTable(name = "thread_message_media", joinColumns = @JoinColumn(name = "message_id"))
    @Column(name = "media_url", length = 2048)
    private List<String> mediaUrls = new ArrayList<>();

    public ThreadMessage(CommunicationThread thread, ThreadWriterType writerType, String content, List<String> mediaUrls) {
        this.thread = thread;
        this.writerType = writerType;
        this.content = content;
        if (mediaUrls != null) {
            this.mediaUrls.addAll(mediaUrls);
        }
    }
}
