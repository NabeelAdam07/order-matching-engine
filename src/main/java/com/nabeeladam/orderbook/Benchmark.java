package com.nabeeladam.orderbook;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Measures throughput (operations per second) and per-operation latency percentiles
 * on a realistic mix of operations around a mid price:
 *   75% limit orders (some cross and trade, most rest), 10% market orders, 15% cancels.
 *
 * Run with: mvn -q compile exec:java
 *
 * Notes on method, which an interviewer may ask about:
 *  - A warm-up phase runs first so the JIT compiler has optimised the hot code before timing starts.
 *  - A fixed random seed makes runs repeatable.
 *  - Latency uses System.nanoTime() around each call, which itself costs roughly 20-30ns,
 *    so the smallest figures are an upper bound. A tool like JMH would be more rigorous.
 */
public final class Benchmark {

    private static final int WARMUP_OPS = 500_000;
    private static final int MEASURED_OPS = 2_000_000;
    private static final long MID_PRICE = 10_000;
    private static final int PRICE_SPREAD = 50;   // limit prices fall within mid +/- 50 ticks

    public static void main(String[] args) {
        System.out.println("Warming up (" + WARMUP_OPS + " ops)...");
        run(WARMUP_OPS, 1L, false);

        System.out.println("Measuring (" + MEASURED_OPS + " ops)...");
        run(MEASURED_OPS, 42L, true);
    }

    private static void run(int ops, long seed, boolean report) {
        OrderBook book = new OrderBook();
        SplittableRandom random = new SplittableRandom(seed);
        long[] liveIds = new long[ops];     // ids we may later try to cancel
        int liveCount = 0;
        long nextId = 1;
        long tradeCount = 0;
        long[] latencies = new long[ops];

        long start = System.nanoTime();
        for (int i = 0; i < ops; i++) {
            int roll = random.nextInt(100);
            long t0 = System.nanoTime();

            if (roll < 75) {
                Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
                long price = MID_PRICE + random.nextInt(-PRICE_SPREAD, PRICE_SPREAD + 1);
                long qty = 1 + random.nextInt(100);
                long id = nextId++;
                tradeCount += book.submit(Order.limit(id, side, price, qty)).size();
                liveIds[liveCount++] = id;
            } else if (roll < 85) {
                Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
                tradeCount += book.submit(Order.market(nextId++, side, 1 + random.nextInt(100))).size();
            } else if (liveCount > 0) {
                int pick = random.nextInt(liveCount);
                book.cancel(liveIds[pick]);                 // may already be filled; that's fine
                liveIds[pick] = liveIds[--liveCount];       // swap-remove from the candidate list
            }

            latencies[i] = System.nanoTime() - t0;
        }
        long elapsed = System.nanoTime() - start;

        if (report) {
            Arrays.sort(latencies);
            double seconds = elapsed / 1e9;
            System.out.printf("%nOperations:      %,d in %.2f s%n", ops, seconds);
            System.out.printf("Throughput:      %,.0f ops/sec%n", ops / seconds);
            System.out.printf("Trades produced: %,d%n", tradeCount);
            System.out.printf("Resting orders:  %,d at end%n", book.restingOrderCount());
            System.out.printf("Latency p50:     %,d ns%n", percentile(latencies, 50));
            System.out.printf("Latency p99:     %,d ns%n", percentile(latencies, 99));
            System.out.printf("Latency p99.9:   %,d ns%n", percentile(latencies, 99.9));
            System.out.printf("Latency max:     %,d ns%n", latencies[latencies.length - 1]);
        }
    }

    private static long percentile(long[] sorted, double p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }
}
