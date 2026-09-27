package showroomz.domain.message.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.entity.ThreadParticipant;
import showroomz.domain.message.repository.MessageRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.repository.ThreadParticipantRepository;
import showroomz.domain.message.type.ParticipantType;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스레드 읽음 위치(28-1 수정계획 3) — 조회 후 저장을 upsert 한 문장으로 바꾼 이유 두 가지를 실제 DB로 확인한다.
 * ① 같은 스레드를 처음 동시에 열어도 유니크 키 충돌이 없다 ② 늦게 커밋된 옛 위치가 새 위치를 덮지 않는다.
 */
@DisplayName("[통합] 스레드 읽음 위치 — 동시 최초 열람 · 읽음 위치 후퇴 방지")
class ThreadReadPositionIntegrationTest extends IntegrationTestSupport {

    @Autowired private UserRepository userRepository;
    @Autowired private CreatorRepository creatorRepository;
    @Autowired private ConnectionRepository connectionRepository;
    @Autowired private MessageThreadRepository threadRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ThreadParticipantRepository participantRepository;
    @Autowired private MessageThreadService messageThreadService;

    private BrandFixture.Brand brand;
    private Creator creator;
    private MessageThread thread;

    @BeforeEach
    void setUpThread() {
        brand = fixture.createBrand("thread-brand@showroomz.test", "글로우랩");
        LocalDateTime now = LocalDateTime.now();
        Users owner = userRepository.save(new Users("creator-jimin", "글로우_지민", "jimin@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, now, now));
        creator = creatorRepository.save(Creator.builder()
                .user(owner).snsType(SnsType.INSTAGRAM).channelUrl("https://instagram.com/jimin").accountId("jimin")
                .followerCount(12_000).businessEmail("jimin-biz@showroomz.test").showroomName("글로우_지민")
                .businessType(CreatorBusinessType.INDIVIDUAL).build());
        Connection connection = Connection.requestPair(brand.market(), creator);
        connection.markConnected();
        thread = threadRepository.save(MessageThread.openFor(connectionRepository.save(connection)));
    }

    @Test
    @DisplayName("참여자 행이 없는 스레드를 여러 요청이 동시에 읽음 처리해도 모두 성공하고 행은 하나 · 위치는 최신 메시지다")
    void concurrentFirstReadCreatesOneRow() throws Exception {
        send(ParticipantType.SELLER, brand.marketId(), "c-1");
        Message latest = send(ParticipantType.SELLER, brand.marketId(), "c-2");

        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    messageThreadService.markRead(thread, ParticipantType.CREATOR, creator.getId());
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);   // 유니크 키 충돌이면 여기서 ExecutionException
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(participantRepository.findAll())
                .filteredOn(p -> p.getParticipantType() == ParticipantType.CREATOR)
                .singleElement()
                .satisfies(p -> assertThat(p.getLastReadMessageId()).isEqualTo(latest.getId()));
        assertThat(unreadOf(ParticipantType.CREATOR, creator.getId())).isZero();
    }

    @Test
    @DisplayName("옛 메시지 id로 늦게 기록돼도 읽음 위치와 읽은 시각은 뒤로 가지 않고, 새 메시지로는 앞으로 간다")
    void readPositionNeverMovesBackward() {
        Message first = send(ParticipantType.CREATOR, creator.getId(), "c-1");
        Message second = send(ParticipantType.CREATOR, creator.getId(), "c-2");
        Message third = send(ParticipantType.CREATOR, creator.getId(), "c-3");
        LocalDateTime base = LocalDateTime.now().withNano(0);

        upsert(second, base);
        upsert(first, base.plusMinutes(1));   // 최신 id를 먼저 읽고 늦게 커밋한 요청

        assertThat(sellerRow().getLastReadMessageId()).isEqualTo(second.getId());
        assertThat(sellerRow().getLastReadAt()).isEqualTo(base);
        assertThat(unreadOf(ParticipantType.SELLER, brand.marketId())).isEqualTo(1);

        upsert(third, base.plusMinutes(2));

        assertThat(sellerRow().getLastReadMessageId()).isEqualTo(third.getId());
        assertThat(sellerRow().getLastReadAt()).isEqualTo(base.plusMinutes(2));
        assertThat(unreadOf(ParticipantType.SELLER, brand.marketId())).isZero();
    }

    private Message send(ParticipantType senderType, Long senderId, String clientMessageId) {
        return messageRepository.save(Message.create(thread, senderType, senderId, clientMessageId, "본문 " + clientMessageId));
    }

    private void upsert(Message message, LocalDateTime at) {
        transactionTemplate.executeWithoutResult(tx -> participantRepository.upsertReadPosition(
                thread.getId(), ParticipantType.SELLER.name(), brand.marketId(), message.getId(), at));
    }

    private ThreadParticipant sellerRow() {
        return participantRepository.findByThreadAndParticipantTypeAndParticipantId(
                thread, ParticipantType.SELLER, brand.marketId()).orElseThrow();
    }

    private long unreadOf(ParticipantType type, Long participantId) {
        return messageThreadService.countUnreadByThreadIds(List.of(thread.getId()), type, participantId)
                .getOrDefault(thread.getId(), 0L);
    }
}
