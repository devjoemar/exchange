package com.devjoemar.exchange.queue;

import com.devjoemar.exchange.domain.OffHeapOrder;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Publishes {@link OffHeapOrder} events to a Chronicle Queue in an off-heap manner.
 *
 * <p><strong>Rationale:</strong> 
 * <ul>
 *   <li>Stores numeric fields as {@code int64} to avoid string-based overhead.</li>
 *   <li>Single-producer approach is typically lock-free at the queue level.</li>
 * </ul>
 */

public class ChronicleQueueOrderPublisher implements AutoCloseable {

    private final ChronicleQueue queue;
    private final ExcerptAppender appender;

    /**
     * Creates the publisher for a single ChronicleQueue file/directory.
     *
     * @param queuePath the path to the Chronicle queue data
     */
    public ChronicleQueueOrderPublisher(Path queuePath) {
        this.queue = ChronicleQueue.singleBuilder(queuePath.toFile()).build();
        this.appender = queue.createAppender();
    }

    /**
     * Publishes an {@link OffHeapOrder} to the queue with minimal serialization overhead.
     */
    public void publishOrder(OffHeapOrder order) {
        appender.writeDocument(wire -> {
            wire.write("orderId").text(order.getOrderId());
            wire.write("orderType").int32(order.getOrderType().ordinal());
            wire.write("price").int64(order.getPrice());
            wire.write("quantity").int64(order.getRemainingQuantity());
        });
    }

    /** Exposes the underlying queue, if needed (e.g., for the matching engine). */
    public ChronicleQueue getQueue() {
        return this.queue;
    }

    @Override
    public void close() {
        queue.close();
    }
}
