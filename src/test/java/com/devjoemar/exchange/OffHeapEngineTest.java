package com.devjoemar.exchange;

import com.devjoemar.exchange.domain.OffHeapOrder;
import com.devjoemar.exchange.domain.OffHeapTrade;
import com.devjoemar.exchange.domain.OrderStatus;
import com.devjoemar.exchange.domain.OrderType;
import com.devjoemar.exchange.engine.OffHeapMatchingEngine;
import com.devjoemar.exchange.engine.OffHeapOrderBook;
import com.devjoemar.exchange.queue.ChronicleQueueOrderPublisher;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Supplier;

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
        publisher = new ChronicleQueueOrderPublisher(tempQueueDir);
        orderBook = new OffHeapOrderBook();
        engine = new OffHeapMatchingEngine(publisher.getQueue(), orderBook);
        engineThread = new Thread(engine, "TestEngineThread");
        engineThread.start();
    }

    @AfterEach
    void teardown() throws Exception {
        engine.shutdown();
        engineThread.join();
        publisher.close();
        Files.walk(tempQueueDir)
                .sorted((p1, p2) -> p2.compareTo(p1))
                .forEach(path -> path.toFile().delete());
    }

    private void waitForCondition(Supplier<Boolean> condition, Duration timeout) throws InterruptedException {
        long endTime = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < endTime) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Condition not met within timeout");
    }

    @Test
    void testBuySellMatch() throws InterruptedException {
        OffHeapOrder sellOrder = new OffHeapOrder("S1", OrderType.SELL, 10000, 5);
        OffHeapOrder buyOrder = new OffHeapOrder("B1", OrderType.BUY, 10100, 5);

        publisher.publishOrder(sellOrder);
        publisher.publishOrder(buyOrder);

        waitForCondition(() -> engine.getTrades().size() == 1, Duration.ofMillis(500));

        assertEquals(1, engine.getTrades().size(), "Should produce 1 trade");
        OffHeapTrade trade = engine.getTrades().peek();
        assertNotNull(trade);
        assertEquals("B1", trade.getBuyOrderId());
        assertEquals("S1", trade.getSellOrderId());
        assertEquals(10000, trade.getTradePrice());
        assertEquals(5, trade.getTradeQuantity());

        OffHeapOrder processedBuyOrder = orderBook.getBuyOrderById("B1");
        OffHeapOrder processedSellOrder = orderBook.getSellOrderById("S1");

        assertNull(processedBuyOrder, "Buy order should be removed as it's filled");
        assertNull(processedSellOrder, "Sell order should be removed as it's filled");
    }
}