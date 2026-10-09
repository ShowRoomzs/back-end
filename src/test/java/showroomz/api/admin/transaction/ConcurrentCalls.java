package showroomz.api.admin.transaction;

import showroomz.global.error.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 거래 관리 동시성 테스트 도우미 — 호출들을 같은 순간에 출발시키고 결과를 호출 순서대로 모은다. 업무 예외는 에러 코드 이름으로 바꿔
 * 돌려준다(어느 쪽이 이겼는지는 테스트가 판정한다). 그 밖의 예외는 그대로 터뜨린다.
 */
final class ConcurrentCalls {

    private ConcurrentCalls() {
    }

    @SafeVarargs
    static List<String> race(Callable<String>... calls) throws Exception {
        CountDownLatch ready = new CountDownLatch(calls.length);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(calls.length)) {
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return call.call();
                    } catch (BusinessException e) {
                        return e.getErrorCode().name();
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
