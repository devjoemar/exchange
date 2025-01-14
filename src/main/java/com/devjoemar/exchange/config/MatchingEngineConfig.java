package com.devjoemar.exchange.config;

import com.devjoemar.exchange.engine.OffHeapMatchingEngine;
import com.devjoemar.exchange.engine.OffHeapOrderBook;
import com.devjoemar.exchange.queue.ChronicleQueueOrderPublisher;
import net.openhft.chronicle.queue.ChronicleQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Spring configuration class for setting up ChronicleQueue, the order book, 
 * and the matching engine.
 */
@Configuration
public class MatchingEngineConfig {

    /**
     * Directory path for Chronicle queue data, from application.properties or environment.
     */
    @Value("${matchingengine.queue.path:/tmp/chronicle-queue}")
    private String queuePath;

    /**
     * A bean for the path to Chronicle queue data.
     */
    @Bean
    public Path queueDirectory() {
        return Path.of(queuePath);
    }

    /**
     * A bean for publishing orders to the ChronicleQueue (used by REST controller).
     */
    @Bean
    public ChronicleQueueOrderPublisher queueOrderPublisher(Path queueDirectory) {
        return new ChronicleQueueOrderPublisher(queueDirectory);
    }

    /**
     * The in-memory order book. If you wanted an off-heap data structure (ChronicleMap), 
     * you would adapt this to create that structure instead.
     */
    @Bean
    public OffHeapOrderBook offHeapOrderBook() {
        return new OffHeapOrderBook();
    }

    /**
     * The matching engine. Reads from the same ChronicleQueue that publisher writes to.
     */
    @Bean
    public OffHeapMatchingEngine offHeapMatchingEngine(ChronicleQueueOrderPublisher publisher,
                                                       OffHeapOrderBook orderBook) {
        ChronicleQueue queue = publisher.getQueue();
        return new OffHeapMatchingEngine(queue, orderBook);
    }

    /**
     * Automatically start the matching engine in a dedicated thread on application startup.
     */
    @Bean
    public Thread matchingEngineThread(OffHeapMatchingEngine engine) {
        Thread t = new Thread(engine, "MatchingEngineThread");
        t.setDaemon(true);
        t.start();
        return t;
    }
}
