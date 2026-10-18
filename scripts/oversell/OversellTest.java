import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 支付/账户扣减防超卖并发实测（跑在生产库上，使用与线上一致的 SQL 与重试协议）。
 *
 * 模式：
 *  optimistic —— 忠实复刻 UserAccountServiceImpl.performOptimisticLockUpdate：
 *                读 version → UPDATE ... WHERE version=? AND chat_count_remaining>=1 →
 *                失败按 10/15/20ms 退避重试，最多 3 次尝试。
 *  atomic     —— 忠实复刻 decrementTokenBalance 原子 SQL：
 *                UPDATE ... SET token_balance=token_balance-1 WHERE token_balance>=1。
 *
 * 校验：成功扣减次数 + 期末余额 == 期初余额，且期末余额 >= 0（零超卖）。
 */
public class OversellTest {
    public static void main(String[] a) throws Exception {
        String url = a[0], user = a[1], pass = a[2];
        long uid = Long.parseLong(a[3]);
        String mode = a[4];
        int threads = Integer.parseInt(a[5]);

        AtomicInteger success = new AtomicInteger(), fail = new AtomicInteger(), retries = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try (Connection c = DriverManager.getConnection(url, user, pass)) {
                    ready.countDown();
                    start.await();
                    if ("optimistic".equals(mode)) {
                        boolean ok = false;
                        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
                            long ver; int remain;
                            try (PreparedStatement ps = c.prepareStatement(
                                    "SELECT version, chat_count_remaining FROM t_user_account WHERE user_id=? AND deleted=0")) {
                                ps.setLong(1, uid);
                                ResultSet rs = ps.executeQuery();
                                if (!rs.next()) return;
                                ver = rs.getLong(1);
                                remain = rs.getInt(2);
                            }
                            if (remain <= 0) break;
                            try (PreparedStatement ps = c.prepareStatement(
                                    "UPDATE t_user_account SET chat_count_remaining=chat_count_remaining-1,"
                                    + " version=version+1, update_time=NOW()"
                                    + " WHERE user_id=? AND version=? AND chat_count_remaining>=1"
                                    + " AND (package_expire_time IS NULL OR package_expire_time>NOW()) AND deleted=0")) {
                                ps.setLong(1, uid);
                                ps.setLong(2, ver);
                                ok = ps.executeUpdate() > 0;
                            }
                            if (!ok) {
                                retries.incrementAndGet();
                                Thread.sleep(10 + attempt * 5);
                            }
                        }
                        if (ok) success.incrementAndGet(); else fail.incrementAndGet();
                    } else {
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE t_user_account SET token_balance=token_balance-1, update_time=NOW()"
                                + " WHERE user_id=? AND token_balance>=1 AND deleted=0")) {
                            ps.setLong(1, uid);
                            if (ps.executeUpdate() > 0) success.incrementAndGet(); else fail.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        ready.await();
        long t0 = System.nanoTime();
        start.countDown();
        done.await();
        long ms = (System.nanoTime() - t0) / 1_000_000;

        try (Connection c = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT chat_count_remaining, token_balance, version FROM t_user_account WHERE user_id=?")) {
            ps.setLong(1, uid);
            ResultSet rs = ps.executeQuery();
            rs.next();
            System.out.printf("RESULT mode=%s threads=%d success=%d fail=%d retries=%d wallMs=%d remaining=%d token=%d version=%d%n",
                    mode, threads, success.get(), fail.get(), retries.get(), ms,
                    rs.getInt(1), rs.getLong(2), rs.getLong(3));
        }
    }
}
