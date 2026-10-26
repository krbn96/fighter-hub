package com.fighterhub.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// POST /api/auth/loginのブルートフォース対策。新規ライブラリを追加せず、Spring Bootアプリ層の
// インメモリ・固定ウィンドウ(15分)方式で、以下2種類のカウンタを独立に持つ。
//   - IP単位: 20回失敗/15分
//   - (IP + 正規化email)単位: 5回失敗/15分
// email単独のキーは持たない(被害者のemailだけを知る第三者が、別IPから何度も失敗させて
// 被害者本人を締め出す、というアカウントロックによるDoSを構造的に避けるため)。
// 単一Lightsailインスタンス・インメモリ実装であるため、アプリ再起動でカウンタは全てリセットされる
// (許容する既知の制約。詳細はclaude-report.md「Week9 Day13：ログインレート制限設計」参照)。
//
// map上限(MAX_TRACKED_ENTRIES)到達時はfail-closed: 新規キーを追跡できない場合、そのログイン
// 試行はブロック(429)として扱う。「追跡できない=無制限に許可」にはしない
// (大量の異なるIP/emailでmapを埋め尽くして、本来の制限を迂回する攻撃を防ぐため)。
// 既存キーのカウンター・有効な制限(ブロック状態)はmap上限の影響を一切受けない。
@Component
public class LoginRateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int IP_THRESHOLD = 20;
    static final int COMBO_THRESHOLD = 5;
    static final int MAX_TRACKED_ENTRIES = 50_000;

    private final Clock clock;

    private final ConcurrentHashMap<String, Window> ipWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> comboWindows = new ConcurrentHashMap<>();

    // 新規キーの「容量確認→追加」を直列化するためのlock。既存キーの参照・更新
    // (computeIfPresent)はlock不要で並行に動作する。新規キー追加だけを直列化することで、
    // 並行実行時にもMAX_TRACKED_ENTRIESを超えて肥大化しないことを保証する
    // (ConcurrentHashMap#sizeの読み取りだけでは、複数スレッドが同時に「まだ空きがある」と
    //  判定して上限を超えて追加してしまう競合を防げないため)。
    private final ReentrantLock ipAdmissionLock = new ReentrantLock();
    private final ReentrantLock comboAdmissionLock = new ReentrantLock();

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        // レコードの自動生成accessor(allowed())と名前が衝突するため、許可側のfactory
        // methodは"allow"という別名にする。
        static Decision allow() {
            return new Decision(true, 0);
        }

        static Decision blocked(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }

    // 実際にログイン処理を行う前に呼び出す。IP単位・(IP+email)単位のいずれかが閾値に
    // 達していればブロックする(どちらが原因でも呼び出し元には同一の扱いをさせる)。
    //
    // 副作用: 対象キーが未追跡(新規または期限切れ)の場合、ここで「予約」としてウィンドウを
    // 作成/更新する。これにより、以降のrecordFailureが必ず既存キーへの加算として
    // 原子的に行える(予約自体がmap上限の対象になり、fail-closedの判定もここで行う)。
    public Decision checkAllowed(String ip, String rawEmail) {
        long now = nowEpochSecond();
        String combo = comboKey(ip, rawEmail);

        Window ipWindow = reserveOrTouch(ipWindows, ipAdmissionLock, ip, now);
        Window comboWindow = reserveOrTouch(comboWindows, comboAdmissionLock, combo, now);

        if (ipWindow == null || comboWindow == null) {
            // map上限に達していて新規キーを追跡できなかった場合(fail-closed)。
            // ウィンドウ自体が存在しないため正確な残り時間は算出できないが、
            // 常に正の妥当な秒数(ウィンドウ長)をRetry-Afterとして返す。
            return Decision.blocked(WINDOW.getSeconds());
        }

        long ipRetryAfter = remainingBlockSeconds(ipWindow, now, IP_THRESHOLD);
        long comboRetryAfter = remainingBlockSeconds(comboWindow, now, COMBO_THRESHOLD);

        long retryAfter = Math.max(ipRetryAfter, comboRetryAfter);
        return retryAfter > 0 ? Decision.blocked(retryAfter) : Decision.allow();
    }

    // 認証401の場合のみ呼び出す(バリデーションエラー等では呼ばない)。
    // checkAllowedが必ず先に呼ばれ、許可(allowed=true)だった場合のみ呼ばれる前提のため、
    // 対象キーは既にcheckAllowedで予約済みのはず(通常はmap上限の影響を受けない)。
    public void recordFailure(String ip, String rawEmail) {
        long now = nowEpochSecond();
        incrementExisting(ipWindows, ip, now);
        incrementExisting(comboWindows, comboKey(ip, rawEmail), now);
    }

    // 認証成功時に呼び出す。(IP+email)キーのみリセットする。
    // IPキーはリセットしない(同じIPの他の利用者による失敗を無効化してしまわないため)。
    public void recordSuccess(String ip, String rawEmail) {
        comboWindows.remove(comboKey(ip, rawEmail));
    }

    // 期限切れエントリの定期清掃。アクセスされないまま放置されたキーが
    // メモリに滞留し続けることを防ぐ(上限到達時のみ清掃する遅延清掃の補完)。
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    void cleanupExpiredEntries() {
        long now = nowEpochSecond();
        removeExpired(ipWindows, now);
        removeExpired(comboWindows, now);
    }

    private long nowEpochSecond() {
        return clock.instant().getEpochSecond();
    }

    private long remainingBlockSeconds(Window window, long now, int threshold) {
        long windowEnd = window.windowStart + WINDOW.getSeconds();
        if (now >= windowEnd) {
            return 0;
        }

        return window.count.get() >= threshold ? windowEnd - now : 0;
    }

    // 既存キーの参照・期限切れ時の再作成はcomputeIfPresentのみで原子的に行える
    // (map拡張を伴わないため、lockは不要)。
    //
    // 新規キーの追加(map拡張)は、容量確認と挿入をadmissionLockで直列化する。
    // 清掃後もなお上限に達している場合はnullを返し、呼び出し元でfail-closed(ブロック)
    // として扱わせる(既存のアクティブな制限は一切変更しない)。
    private Window reserveOrTouch(
            ConcurrentHashMap<String, Window> map, ReentrantLock admissionLock, String key, long now) {

        Window touched = map.computeIfPresent(key, (k, existing) ->
                isExpired(existing, now) ? new Window(now, new AtomicInteger(0)) : existing);
        if (touched != null) {
            return touched;
        }

        admissionLock.lock();
        try {
            // lock取得までの間に他スレッドが同じキーを作成済みの可能性があるため再確認する。
            Window alreadyCreated = map.get(key);
            if (alreadyCreated != null) {
                return isExpired(alreadyCreated, now)
                        ? map.compute(key, (k, existing) -> new Window(now, new AtomicInteger(0)))
                        : alreadyCreated;
            }

            if (map.size() >= MAX_TRACKED_ENTRIES) {
                removeExpired(map, now);
                if (map.size() >= MAX_TRACKED_ENTRIES) {
                    return null;
                }
            }

            Window created = new Window(now, new AtomicInteger(0));
            map.put(key, created);
            return created;
        } finally {
            admissionLock.unlock();
        }
    }

    // checkAllowedで予約済みのキーへの加算。通常は既存キーのはずだが、万一未追跡の場合も
    // (呼び出し順序の不整合等への防御として)カウント1で新規作成する。
    private void incrementExisting(ConcurrentHashMap<String, Window> map, String key, long now) {
        map.compute(key, (k, existing) -> {
            if (existing == null || isExpired(existing, now)) {
                return new Window(now, new AtomicInteger(1));
            }

            existing.count.incrementAndGet();
            return existing;
        });
    }

    private boolean isExpired(Window window, long now) {
        return now - window.windowStart >= WINDOW.getSeconds();
    }

    private void removeExpired(ConcurrentHashMap<String, Window> map, long now) {
        map.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    // emailは生のまま保持せず、正規化(trim+小文字化)した上でSHA-256ハッシュ化してキーに含める
    // (メモリ上に生のメールアドレスを残さない。ログイン認証自体の大文字小文字判定には影響しない
    // -このキー生成はレート制限専用で、AuthServiceの既存の認証ロジックとは独立)。
    private String comboKey(String ip, String rawEmail) {
        String normalizedEmail = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        return ip + "|" + sha256Hex(normalizedEmail);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private static final class Window {
        private final long windowStart;
        private final AtomicInteger count;

        Window(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
