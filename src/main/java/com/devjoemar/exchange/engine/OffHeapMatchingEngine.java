package com.devjoemar.exchange.engine;

import com.devjoemar.exchange.domain.OffHeapOrder;
import com.devjoemar.exchange.domain.OffHeapTrade;
import com.devjoemar.exchange.domain.OrderType;
import net.openhft.chronicle.queue.ChronicleQueue;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A single-threaded matching engine that processes incoming orders from a ChronicleQueue
 * and manages order matching using an {@link OffHeapOrderBook}. This design ensures low
 * latency and high performance by avoiding locking mechanisms and minimizing garbage collection
 * overhead.
 *
 * <p><strong>Rationale:</strong></p>
 * <ul>
 *   <li><strong>Single-Threaded Design:</strong> By running in a dedicated thread, this engine
 *       avoids the complexities and overhead of multi-threaded synchronization, ensuring consistent
 *       and predictable order processing.</li>
 *   <li><strong>Low Latency Order Processing:</strong> Orders are read and processed as primitive
 *       types (longs) to reduce memory allocation and garbage collection, which is crucial for high-frequency
 *       trading applications.</li>
 *   <li><strong>Chronicle Queue Integration:</strong> Utilizes Chronicle Queue for efficient,
 *       low-latency message passing, suitable for high-throughput scenarios.</li>
 * </ul>
 */
@Component
public class OffHeapMatchingEngine implements Runnable {

    /**
     * The Chronicle Queue from which incoming orders are read.
     */
    private final ChronicleQueue inputQueue;

    /**
     * The in-memory order book that manages and matches buy and sell orders.
     */
    private final OffHeapOrderBook orderBook;

    /**
     * An atomic flag to control the execution of the processing loop.
     */
    private final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * Initializes the matching engine with the specified queue and order book.
     *
     * @param inputQueue the ChronicleQueue from which orders are read
     * @param orderBook  the order book used for matching logic
     */
    public OffHeapMatchingEngine(ChronicleQueue inputQueue, OffHeapOrderBook orderBook) {
        this.inputQueue = inputQueue;
        this.orderBook = orderBook;
    }

    /**
     * The main processing loop that reads orders from the Chronicle Queue and processes them
     * using the {@link OffHeapOrderBook}. This method runs in a dedicated thread, ensuring
     * single-threaded execution for low-latency and high-throughput order processing.
     *
     * <p><strong>Rationale:</strong></p>
     * <ul>
     *   <li><strong>Chronicle Queue Tailer:</strong> A Tailer is used to read orders from the
     *       Chronicle Queue. It ensures that orders are processed in the exact sequence they were
     *       published, maintaining the order of operations.</li>
     *   <li><strong>Single-Threaded Execution:</strong> By processing orders in a single thread,
     *       we avoid concurrency issues and simplify the logic, which is crucial for low-latency
     *       applications.</li>
     *   <li><strong>Efficient Waiting:</strong> When no data is available, {@code Thread.onSpinWait()}
     *       is called to yield the CPU efficiently, reducing overhead compared to traditional sleep
     *       methods.</li>
     *   <li><strong>Low-Latency Design:</strong> The combination of Chronicle Queue and single-threaded
     *       processing ensures minimal garbage collection and fast order handling, suitable for high-frequency
     *       trading scenarios.</li>
     * </ul>
     *
     * <p><strong>Processing Flow:</strong></p>
     * <ol>
     *   <li>The Tailer reads orders from the queue in a loop as long as the engine is running.</li>
     *   <li>For each order, it extracts the order details (orderId, orderType, price, quantity) and
     *       creates an {@link OffHeapOrder} object.</li>
     *   <li>The order is then processed by the order book, which manages matching and execution logic.</li>
     *   <li>If no data is available, the thread spins wait to yield the CPU efficiently.</li>
     * </ol>
     *
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li>Exception handling is managed at a higher level to ensure the engine remains robust and
     *       continues processing even in the face of transient errors.</li>
     * </ul>
     */
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

                // Yield the CPU if no data is available to avoid wasting resources
                if (!foundData) {
                    Thread.onSpinWait();
                }
            }
        }
    }

    /**
     * Gracefully shuts down the processing loop by setting the running flag to false.
     */
    public void shutdown() {
        running.set(false);
    }

    /**
     * Retrieves all trades generated by the order book.
     *
     * @return a deque containing all trades
     */
    public Deque<OffHeapTrade> getTrades() {
        return orderBook.getTrades();
    }
}