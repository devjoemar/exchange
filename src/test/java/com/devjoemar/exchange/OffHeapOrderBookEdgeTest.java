package com.devjoemar.exchange;


import com.devjoemar.exchange.domain.OffHeapOrder;
import com.devjoemar.exchange.domain.OffHeapTrade;
import com.devjoemar.exchange.domain.OrderType;
import com.devjoemar.exchange.engine.OffHeapOrderBook;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for edge cases in the OffHeapOrderBook, focusing on 
 * boundary/invalid inputs and specialized scenarios.
 */
class OffHeapOrderBookEdgeTest {

    private OffHeapOrderBook orderBook;

    @BeforeEach
    void setup() {
        orderBook = new OffHeapOrderBook();
    }

    @Test
    void testZeroQuantityOrder_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new OffHeapOrder("O1", OrderType.BUY, 10000, 0),
            "Quantity=0 should throw IllegalArgumentException");
    }

    @Test
    void testNegativePriceOrder_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new OffHeapOrder("O2", OrderType.SELL, -100, 10),
            "Negative price should throw exception");
    }

    @Test
    void testZeroPriceOrder_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> new OffHeapOrder("O3", OrderType.BUY, 0, 10),
            "Zero price must throw exception");
    }

    @Test
    void testCancelOrder_SkipDuringMatching() {
        // SELL 10 @ price=10000
        OffHeapOrder sellOrder = new OffHeapOrder("S1", OrderType.SELL, 10000, 10);
        orderBook.processOrder(sellOrder);

        // Cancel this order before matching
        sellOrder.cancel();

        // BUY 5 @ price=11000, which would have matched, but the order is canceled
        OffHeapOrder buyOrder = new OffHeapOrder("B1", OrderType.BUY, 11000, 5);
        orderBook.processOrder(buyOrder);

        // No trades should be generated because the SELL was canceled
        assertTrue(orderBook.getTrades().isEmpty(), "Canceled SELL must not produce trades");

        // The BUY should remain on buySide
        assertFalse(orderBook.getBuySide().isEmpty(), "BUY order remains open since it did not match");
    }

    @Test
    void testMultiplePartialFills_SamePriceLevel() {
        // SELL 10 @ price=10000
        OffHeapOrder sellOrder = new OffHeapOrder("S1", OrderType.SELL, 10000, 10);
        orderBook.processOrder(sellOrder);

        // BUY 3 @ price=10000
        OffHeapOrder buyOrder1 = new OffHeapOrder("B1", OrderType.BUY, 10000, 3);
        // BUY 3 @ price=10000
        OffHeapOrder buyOrder2 = new OffHeapOrder("B2", OrderType.BUY, 10000, 3);
        // BUY 5 @ price=10000
        OffHeapOrder buyOrder3 = new OffHeapOrder("B3", OrderType.BUY, 10000, 5);

        orderBook.processOrder(buyOrder1);
        orderBook.processOrder(buyOrder2);
        orderBook.processOrder(buyOrder3);

        // SELL had 10, first 3 filled by B1, next 3 filled by B2, remaining 4 filled by B3 => total = 10
        // B3 had 5, so after filling 4, B3 remains with 1 unit
        // So we expect 3 trades total.

        var tList = orderBook.getTrades();
        assertEquals(3, tList.size(), "Should generate three trades in total for partial fills.");

        long totalQty = tList.stream().mapToLong(OffHeapTrade::getTradeQuantity).sum();
        assertEquals(10, totalQty, "All 10 SELL units should be matched.");

        // Check that B3 still has 1 unit left
        var buySide = orderBook.getBuySide();
        // B3 remains in the queue with quantity=1
        assertFalse(buySide.isEmpty());
        var bestEntry = buySide.firstEntry();
        var queue = bestEntry.getValue();
        // Should contain just B3
        OffHeapOrder leftover = queue.peek();
        assertEquals("B3", leftover.getOrderId());
        assertEquals(1, leftover.getRemainingQuantity());
    }


    @Test
    void testSellPricedTooHigh_RemainsUnmatched() {
        // BUY 5 @ price=9000
        OffHeapOrder buyOrder = new OffHeapOrder("B1", OrderType.BUY, 9000, 5);
        orderBook.processOrder(buyOrder);

        // SELL 5 @ price=10000
        OffHeapOrder sellOrder = new OffHeapOrder("S1", OrderType.SELL, 10000, 5);
        orderBook.processOrder(sellOrder);

        // No trades because SELL is higher than best BID(9000)
        assertTrue(orderBook.getTrades().isEmpty());

        // SELL should remain on sellSide
        assertFalse(orderBook.getSellSide().isEmpty());
    }

}
