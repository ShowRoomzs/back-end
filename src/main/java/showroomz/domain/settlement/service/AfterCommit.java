package showroomz.domain.settlement.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * 커밋 뒤 실행 — 알림 훅처럼 롤백되면 나가면 안 되는 일. 트랜잭션 밖이면 바로 실행한다.
 *
 * <p><b>커밋 뒤 콜백 안에서 다시 부르면 바로 실행한다</b>(1017 알림함). 콜백 단계에서도 트랜잭션 자원 · 동기화는 아직 묶여 있어서,
 * 거기서 동기화를 새로 등록하면 이미 끝난 트랜잭션에 붙어 영영 실행되지 않는다(Spring 은 afterCommit 목록을 복사해 돈다). 이미 커밋된
 * 뒤이므로 바로 실행하는 것이 같은 뜻이다. 콜백 안에서 새 트랜잭션(REQUIRES_NEW)을 열었다면 그 트랜잭션의 커밋 뒤로 등록한다 —
 * 트랜잭션 이름으로 가린다.
 */
public final class AfterCommit {

    /** 지금 커밋 뒤 콜백을 실행 중인 트랜잭션의 이름 — 콜백 밖이면 null. */
    private static final ThreadLocal<String> RUNNING = new ThreadLocal<>();
    private static final String UNNAMED = "<unnamed>";

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || inCompletedTransaction()) {
            action.run();
            return;
        }
        String name = currentName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                String previous = RUNNING.get();
                RUNNING.set(name);
                try {
                    action.run();
                } finally {
                    if (previous == null) {
                        RUNNING.remove();
                    } else {
                        RUNNING.set(previous);
                    }
                }
            }
        });
    }

    private static boolean inCompletedTransaction() {
        String running = RUNNING.get();
        return running != null && Objects.equals(running, currentName());
    }

    private static String currentName() {
        String name = TransactionSynchronizationManager.getCurrentTransactionName();
        return name == null ? UNNAMED : name;
    }
}
