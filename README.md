# Order Matching Engine

A limit order book and matching engine in Java 21: the component at the centre of every stock exchange. It accepts buy and sell orders, matches them by **price-time priority**, and keeps a live book of orders waiting to trade.

## What it does

- **Limit orders** trade at their price or better; any unfilled quantity rests in the book.
- **Market orders** trade immediately at the best available prices; any remainder is dropped.
- **Cancels** remove a resting order by id.
- Every match produces a **trade** at the resting order's price.
- **20 unit tests** cover every matching rule, and a **benchmark** reports throughput and latency percentiles.

## Matching rules (price-time priority)

1. **Price first.** A buy matches the lowest ask; a sell matches the highest bid.
2. **Then time.** At the same price, the order that arrived first is filled first.
3. **The resting order sets the price.** A buy at 105 against an ask at 100 trades at 100.
4. A partially filled resting order **keeps its place** in the queue.

## Design decisions

| Decision | Why |
| --- | --- |
| Prices are `long` ticks, not `double` | Floating point can't represent most decimals exactly (`0.1 + 0.2 != 0.3`). An exchange must compare prices exactly. |
| `TreeMap<price, queue>` per side | Keeps prices sorted, so the best price is `firstEntry()` in O(log n). Bids use a reversed comparator so "first" always means "best" on both sides. |
| `ArrayDeque` per price level | Arrival order *is* time priority: no timestamps or sorting needed, and add/remove at the ends is O(1). |
| `HashMap<id, order>` | Cancel finds an order in O(1) instead of searching the book. |
| Single-threaded | Real engines process one instrument's orders in strict sequence. Results are deterministic and there are no locks. |

## Run it

Needs Java 21 and Maven (`brew install openjdk@21 maven` on a Mac).

```bash
mvn test                  # run the 20 unit tests
mvn -q compile exec:java  # run the benchmark
```

## Benchmark

2,000,000 operations after a 500,000-operation warm-up, with a fixed random seed: 75% limit orders, 10% market orders, 15% cancels, priced within ±50 ticks of the mid.

| Metric | Result |
| --- | --- |
| Throughput | _run on your machine_ ops/sec |
| Latency p50 | _run on your machine_ ns |
| Latency p99 | _run on your machine_ ns |

Latency is measured with `System.nanoTime()` around each call, which adds roughly 20–30 ns, so small figures are upper bounds. The worst-case outliers come from garbage collection pauses.

## Known limitations and next steps

- **Cancel is O(k)** in the number of orders at that price, because `ArrayDeque.remove` scans the queue. A doubly linked list with a pointer stored on each order would make cancel O(1).
- **Garbage collection pauses** cause the latency outliers. Production engines reuse order objects (object pooling) to avoid allocating during trading.
- One instrument per book; a real exchange runs one book per symbol.
- No network API yet. Next step: a small REST or WebSocket service in front of the book.
