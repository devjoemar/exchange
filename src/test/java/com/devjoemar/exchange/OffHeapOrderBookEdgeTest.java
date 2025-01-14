package com.devjoemar.exchange;

import com.devjoemar.exchange.domain.OffHeapOrder;
import com.devjoemar.exchange.domain.OrderStatus;
import com.devjoemar.exchange.domain.OrderType;
import com.devjoemar.exchange.engine.OffHeapOrderBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OffHeapOrderBookEdgeTest {

    private OffHeapOrderBook orderBook;

    @BeforeEach
    void setup() {
        orderBook = new OffHeapOrderBook();
    }

    @Test
    void testFullFillBuyOrder() {
        OffHeapOrder sellOrder = new OffHeapOrder("S1", OrderType.SELL, 10000, 5);
        OffHeapOrder buyOrder = new OffHeapOrder("B1", OrderType.BUY, 10100, 5);

        orderBook.processOrder(sellOrder);
        orderBook.processOrder(buyOrder);

        assertEquals(1, orderBook.getTrades().size());
        assertEquals(OrderStatus.FILLED, buyOrder.getStatus());
        assertEquals(OrderStatus.FILLED, sellOrder.getStatus());
    }

    @Test
    void testFullFillSellOrder() {
        OffHeapOrder buyOrder = new OffHeapOrder("B1", OrderType.BUY, 10100, 5);
        OffHeapOrder sellOrder = new OffHeapOrder("S1", OrderType.SELL, 10000, 5);

        orderBook.processOrder(buyOrder);
        orderBook.processOrder(sellOrder);

        assertEquals(1, orderBook.getTrades().size());
        assertEquals(OrderStatus.FILLED, buyOrder.getStatus());
        assertEquals(OrderStatus.FILLED, sellOrder.getStatus());
    }

    @Test
    void testPartialFillBuyOrder() {
        OffHeapOrder sellOrder1 = new OffHeapOrder("S1", OrderType.SELL, 10000, 3);
        OffHeapOrder sellOrder2 = new OffHeapOrder("S2", OrderType.SELL, 10000, 2);
        OffHeapOrder buyOrder = new OffHeapOrder("B1", OrderType.BUY, 10100, 6); // Quantity set to 6 for partial fill

        orderBook.processOrder(sellOrder1);
        orderBook.processOrder(sellOrder2);
        orderBook.processOrder(buyOrder);

        assertEquals(2, orderBook.getTrades().size(), "Two trades should be generated");
        assertEquals(OrderStatus.PARTIALLY_FILLED, buyOrder.getStatus(), "Buy order should be partially filled");
        assertEquals(1, buyOrder.getRemainingQuantity(), "Buy order should have remaining quantity");
        assertEquals(OrderStatus.FILLED, sellOrder1.getStatus(), "Sell order 1 should be filled");
        assertEquals(OrderStatus.FILLED, sellOrder2.getStatus(), "Sell order 2 should be filled");
    }

    // Additional test cases can be added here following the same structure
}