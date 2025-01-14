package com.devjoemar.exchange;

import com.devjoemar.exchange.domain.OffHeapOrder;
import com.devjoemar.exchange.domain.OrderType;
import com.devjoemar.exchange.engine.OffHeapMatchingEngine;
import com.devjoemar.exchange.engine.OffHeapOrderBook;
import com.devjoemar.exchange.queue.ChronicleQueueOrderPublisher;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OffHeapEngineTest {

    private Path tempQueueDir;
    private ChronicleQueueOrderPublisher publisher;
    private OffHeapOrderBook orderBook;
    private OffHeapMatchingEngine engine;
    private Thread engineThread;

    @BeforeEach
    void setup() throws Exception {
        tempQueueDir = Files.createTempDirectory("cq-test");
        // Create publisher
        publisher = new ChronicleQueueOrderPublisher(tempQueueDir);
        // Create orderBook + engine
        orderBook = new OffHeapOrderBook();
        engine = new OffHeapMatchingEngine(publisher.getQueue(), orderBook);

        // Start engine in a separate thread
        engineThread = new Thread(engine, "TestEngineThread");
        engineThread.start();
    }

    @AfterEach
    void teardown() throws Exception {
        // Cleanly shutdown
        engine.shutdown();
        engineThread.join();
        publisher.close();
        // Remove temp data
        Files.walk(tempQueueDir)
             .sorted((p1, p2) -> p2.compareTo(p1))
             .forEach(path -> path.toFile().delete());
    }

    @Test
    void testBuySellMatch() throws InterruptedException {
        // SELL 5 units @ price=10000
        OffHeapOrder sellOrder = new OffHeapOrder("S1", OrderType.SELL, 10000, 5);
        // BUY 5 units @ price=10100
        OffHeapOrder buyOrder = new OffHeapOrder("B1", OrderType.BUY, 10100, 5);

        publisher.publishOrder(sellOrder);
        publisher.publishOrder(buyOrder);

        // Wait a bit for matching
        Thread.sleep(200);

        assertEquals(1, engine.getTrades().size(), "Should produce 1 trade");
        var trade = engine.getTrades().peek();
        assertNotNull(trade);
        assertEquals("B1", trade.getBuyOrderId());
        assertEquals("S1", trade.getSellOrderId());
        // Usually matches at SELL price
        assertEquals(10000, trade.getTradePrice());
        assertEquals(5, trade.getTradeQuantity());
    }
}
