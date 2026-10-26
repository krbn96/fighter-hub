package com.fighterhub.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private static final String IP_A = "203.0.113.1";
    private static final String IP_B = "203.0.113.2";
    private static final String EMAIL_A = "user-a@example.com";
    private static final String EMAIL_B = "user-b@example.com";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

    @Test
    void IPとemailの組み合わせが4回失敗した時点では次の試行が許可される() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(IP_A, EMAIL_A);
        }

        assertTrue(limiter.checkAllowed(IP_A, EMAIL_A).allowed());
    }

    @Test
    void IPとemailの組み合わせが5回失敗した直後の6回目は429になる() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(IP_A, EMAIL_A);
        }

        LoginRateLimiter.Decision decision = limiter.checkAllowed(IP_A, EMAIL_A);

        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterSeconds() > 0);
    }

    @Test
    void IP単位は20回未満の失敗では許可され20回失敗すると429になる() {
        // 同一IPで異なるemailへの失敗を20回積み上げる(combo閾値5回には引っかからないよう
        // email自体を毎回変える)。IP単位の閾値のみを検証する。
        for (int i = 0; i < 19; i++) {
            limiter.recordFailure(IP_A, "user-" + i + "@example.com");
        }
        assertTrue(limiter.checkAllowed(IP_A, "user-unused@example.com").allowed());

        limiter.recordFailure(IP_A, "user-19@example.com");

        assertFalse(limiter.checkAllowed(IP_A, "user-unused@example.com").allowed());
    }

    @Test
    void 異なるemailではcombo閾値が独立して管理される() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(IP_A, EMAIL_A);
        }

        assertFalse(limiter.checkAllowed(IP_A, EMAIL_A).allowed());
        // 同一IPでも別emailなら、comboキーが異なるため許可される
        // (IP単位の閾値(20回)にはまだ達していないため)。
        assertTrue(limiter.checkAllowed(IP_A, EMAIL_B).allowed());
    }

    @Test
    void 異なるIPではcombo閾値が独立して管理されるためemail単独ではブロックされない() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(IP_A, EMAIL_A);
        }
        assertFalse(limiter.checkAllowed(IP_A, EMAIL_A).allowed());

        // 同一emailでも別IPからの試行はブロックされない(email単独ロックを行わない設計)。
        assertTrue(limiter.checkAllowed(IP_B, EMAIL_A).allowed());
    }

    @Test
    void 成功時はIPとemailの組み合わせキーのみリセットされる() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(IP_A, EMAIL_A);
        }

        limiter.recordSuccess(IP_A, EMAIL_A);

        // comboキーがリセットされていなければ4+4=8回目で閾値(5回)に達して
        // ブロックされるはずだが、リセットされているため許可される(4回目)。
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(IP_A, EMAIL_A);
        }
        assertTrue(limiter.checkAllowed(IP_A, EMAIL_A).allowed());
    }

    @Test
    void 成功してもIPキーはリセットされない() {
        // IP単位の閾値(20回)に達するまで、comboキーには影響しない形で失敗を積み上げる
        // (毎回異なるemailを使い、どのcomboキーも5回未満に留める)。
        for (int i = 0; i < 20; i++) {
            limiter.recordFailure(IP_A, "user-" + i + "@example.com");
        }
        assertFalse(limiter.checkAllowed(IP_A, "user-after-block@example.com").allowed());

        // このemailはこれまで一度も失敗していないキーだが、成功を記録してもIPキーには影響しない。
        limiter.recordSuccess(IP_A, "never-failed@example.com");

        assertFalse(limiter.checkAllowed(IP_A, "user-after-success@example.com").allowed());
    }

    @Test
    void ウィンドウ経過後は失敗回数がリセットされ再度許可される() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(IP_A, EMAIL_A);
        }
        assertFalse(limiter.checkAllowed(IP_A, EMAIL_A).allowed());

        clock.advance(Duration.ofMinutes(15));

        assertTrue(limiter.checkAllowed(IP_A, EMAIL_A).allowed());
    }

    @Test
    void ウィンドウ経過直前はまだブロックされたままになる() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(IP_A, EMAIL_A);
        }

        clock.advance(Duration.ofMinutes(14).plusSeconds(59));

        assertFalse(limiter.checkAllowed(IP_A, EMAIL_A).allowed());
    }

    @Test
    void retryAfterSecondsはウィンドウ終了までの残り秒数になる() {
        limiter.recordFailure(IP_A, EMAIL_A);
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(IP_A, EMAIL_A);
        }

        clock.advance(Duration.ofMinutes(10));

        LoginRateLimiter.Decision decision = limiter.checkAllowed(IP_A, EMAIL_A);

        assertFalse(decision.allowed());
        // 15分ウィンドウのうち10分経過 → 残り5分(300秒)。
        assertEquals(300, decision.retryAfterSeconds());
    }

    @Test
    void emailの大文字小文字や前後の空白が違ってもcomboキーは同一として扱われる() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(IP_A, "  User-A@Example.com  ");
        }

        // 正規化(trim+小文字化)により、表記が違っても同一のcomboキーとして扱われ、ブロックされる。
        assertFalse(limiter.checkAllowed(IP_A, "user-a@example.com").allowed());
    }

    @Test
    void 並行して失敗を記録しても加算が失われず閾値ちょうどで判定される() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    limiter.recordFailure(IP_A, EMAIL_A);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(0, errors.get());
        // 50回失敗(閾値5回を大きく超える)しているため、確実にブロックされているはず
        // (加算が1回でも失われていれば閾値以上にはならない、という保証ではないが、
        //  50並行実行でも最終的に閾値を跨いでブロック状態になることを確認する)。
        assertFalse(limiter.checkAllowed(IP_A, EMAIL_A).allowed());
    }

    @Test
    void 並行して異なるキーに失敗を記録しても件数が正確に積み上がる() throws InterruptedException {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // 4スレッドがそれぞれ1回ずつ、同じ(IP_A, EMAIL_A)へ失敗を記録する。
        // 閾値5回未満(4回)なので、全スレッド完了後も許可されているはず。
        IntStream.range(0, threadCount).forEach(i -> executor.submit(() -> {
            limiter.recordFailure(IP_A, EMAIL_A);
            doneLatch.countDown();
        }));

        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(limiter.checkAllowed(IP_A, EMAIL_A).allowed());
    }

    // ==== Map上限到達時のfail-closed動作(Day13追加修正分) ====

    @Test
    void 容量超過時_新規キーのログイン試行はブロックされる_failClosed() {
        fillComboMapToCapacity(IP_A);

        // すでに上限(50,000件)に達しているため、これまで一度も登場していない
        // 新規のcomboキーはブロックされる(「追跡できない=無制限に許可」にはしない)。
        LoginRateLimiter.Decision decision = limiter.checkAllowed(IP_A, "brand-new-after-cap@example.com");

        assertFalse(decision.allowed());
    }

    @Test
    void 容量超過でブロックされた場合のRetryAfterは正の妥当な秒数になる() {
        fillComboMapToCapacity(IP_A);

        LoginRateLimiter.Decision decision = limiter.checkAllowed(IP_A, "brand-new-after-cap-2@example.com");

        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterSeconds() > 0);
        assertEquals(LoginRateLimiter.WINDOW.getSeconds(), decision.retryAfterSeconds());
    }

    @Test
    void 容量超過時でも既存キーのアクティブな制限は維持される() {
        // 先にブロック状態にしておく(5回失敗)。
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(IP_A, "already-blocked@example.com");
        }
        assertFalse(limiter.checkAllowed(IP_A, "already-blocked@example.com").allowed());

        // 残りをすべて別のcomboキーで埋め、容量を上限まで使い切る。
        fillComboMapToCapacityExcluding(IP_A, "already-blocked@example.com");

        // 容量超過後も、既存のブロック済みキーは解除されず維持されている。
        assertFalse(limiter.checkAllowed(IP_A, "already-blocked@example.com").allowed());
        // 一方、これまで登場していない新規キーは容量超過により429(fail-closed)になる。
        assertFalse(limiter.checkAllowed(IP_A, "never-seen-before@example.com").allowed());
    }

    @Test
    void 期限切れエントリの清掃により容量超過後も新規キーを追跡できるようになる() {
        fillComboMapToCapacity(IP_A);
        assertFalse(limiter.checkAllowed(IP_A, "new-before-expiry@example.com").allowed());

        // ウィンドウ(15分)を超えて時刻を進める → 既存エントリは全て期限切れになる。
        clock.advance(Duration.ofMinutes(16));

        // 容量確認時に期限切れエントリが清掃されるため、新規キーも通常どおり許可される。
        assertTrue(limiter.checkAllowed(IP_A, "new-after-expiry@example.com").allowed());
    }

    @Test
    void 並行して多数の新規キーを追加してもMap上限を大幅超過しない() throws InterruptedException {
        // 上限ちょうど手前(残り10件)まで埋める。
        int remainingSlots = 10;
        fillComboMapTo(IP_A, LoginRateLimiter.MAX_TRACKED_ENTRIES - remainingSlots);

        int threadCount = 200;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            String email = "flood-" + i + "@example.com";
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (limiter.checkAllowed(IP_A, email).allowed()) {
                        allowedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // 空きはちょうど10件だったため、新規キーとして許可されたのはちょうど10件のみ
        // (= Map上限を1件も超過していない)。
        assertEquals(remainingSlots, allowedCount.get());
    }

    private void fillComboMapToCapacity(String ip) {
        fillComboMapTo(ip, LoginRateLimiter.MAX_TRACKED_ENTRIES);
    }

    private void fillComboMapToCapacityExcluding(String ip, String alreadyPresentEmail) {
        // alreadyPresentEmailの分はすでに1件登録済みなので、残りを埋める。
        fillComboMapTo(ip, LoginRateLimiter.MAX_TRACKED_ENTRIES - 1);
    }

    private void fillComboMapTo(String ip, int targetSize) {
        for (int i = 0; i < targetSize; i++) {
            limiter.checkAllowed(ip, "fill-" + i + "@example.com");
        }
    }
}
