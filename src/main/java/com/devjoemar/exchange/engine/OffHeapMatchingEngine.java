package com.devjoemar.exchange.engine;

import com.devjoemar.exchange.domain.OffHeapOrder;
import com.devjoemar.exchange.domain.OffHeapTrade;
import com.devjoemar.exchange.domain.OrderType;
import net.openhft.chronicle.queue.ChronicleQueue;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A single-threaded matching engine that tails a ChronicleQueue, 
 * parses incoming orders, and processes them in an {@link OffHeapOrderBook}.
 *
 * <p><strong>Rationale:</strong></p>
 * <ul>
 *   <li>Runs a continuous loop in a separate thread (avoid locking by single-thread design).</li>
 *   <li>Reads numeric fields (price/quantity) as longs, minimizing GC overhead.</li>
 * </ul>
 */
@Component
public class OffHeapMatchingEngine implements Runnable {

    private final ChronicleQueue inputQueue;
    private final OffHeapOrderBook orderBook;
    private final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * @param inputQueue a ChronicleQueue from which orders are read
     * @param orderBook  the in-memory order book for matching logic
     */
    public OffHeapMatchingEngine(ChronicleQueue inputQueue, OffHeapOrderBook orderBook) {
        this.inputQueue = inputQueue;
        this.orderBook = orderBook;
    }

    @Override
    public void run() {
        try (var tailer = inputQueue.createTailer()) {
            while (running.get()) {
                boolean foundData = tailer.readDocument(wireIn -> {
                    String orderId = wireIn.read("orderId").text();
                    int orderTypeOrdinal = wireIn.read("orderType").int32();
                    long price = wireIn.read("price").int64();
                    long quantity = wireIn.read("quantity").int64();

                    OffHeapOrder order = new OffHeapOrder(
                            orderId,
                            OrderType.values()[orderTypeOrdinal],
                            price,
                            quantity
                    );
                    orderBook.processOrder(order);
                });

                // If no data, spin or yield
                if (!foundData) {
                    Thread.onSpinWait();
                }
            }
        }
    }

    /**
     * Gracefully stops the run loop.
     */
    public void shutdown() {
        running.set(false);
    }

    /**
     * Returns all trades from the underlying order book.
     */
    public Deque<OffHeapTrade> getTrades() {
        return orderBook.getTrades();
    }
}
