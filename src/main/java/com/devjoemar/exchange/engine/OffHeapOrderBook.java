package com.devjoemar.exchange.engine;

import com.devjoemar.exchange.domain.OffHeapOrder;
import com.devjoemar.exchange.domain.OffHeapTrade;
import com.devjoemar.exchange.domain.OrderStatus;
import com.devjoemar.exchange.domain.OrderType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A limit order book that manages buy and sell orders, matching them based on price levels and quantities.
 * It uses TreeMap for efficient insertion and retrieval, with buy orders in descending order and sell orders in ascending order.
 */
public class OffHeapOrderBook {

    /**
     * NavigableMap to hold buy orders with prices as keys in descending order.
     * Each price level contains a Deque of OffHeapOrder instances.
     */
    private final NavigableMap<Long, Deque<OffHeapOrder>> buySide;

    /**
     * NavigableMap to hold sell orders with prices as keys in ascending order.
     * Each price level contains a Deque of OffHeapOrder instances.
     */
    private final NavigableMap<Long, Deque<OffHeapOrder>> sellSide;

    /**
     * Deque to hold all the trades generated from matching orders.
     */
    private final Deque<OffHeapTrade> trades;

    /**
     * ConcurrentHashMap to track buy orders by their IDs for easy lookup and removal when they are filled.
     */
    private final ConcurrentMap<String, OffHeapOrder> buyOrdersById = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap to track sell orders by their IDs for easy lookup and removal when they are filled.
     */
    private final ConcurrentMap<String, OffHeapOrder> sellOrdersById = new ConcurrentHashMap<>();

    /**
     * Initializes the buySide, sellSide, and trades with the appropriate data structures.
     * Buy orders are stored in descending order of price, and sell orders in ascending order of price.
     */
    public OffHeapOrderBook() {
        this.buySide = new TreeMap<>(Comparator.reverseOrder());
        this.sellSide = new TreeMap<>();
        this.trades = new ArrayDeque<>();
    }

    /**
     * Processes an incoming order by adding it to the respective side of the order book and attempting to match it.
     *
     * @param order the incoming order to be processed
     */
    public void processOrder(OffHeapOrder order) {
        if (order.getOrderType() == OrderType.BUY) {
            buyOrdersById.put(order.getOrderId(), order);
            matchBuyOrder(order);
        } else {
            sellOrdersById.put(order.getOrderId(), order);
            matchSellOrder(order);
        }
    }

    /**
     * Matches a buy order against the sell side of the order book.
     * It checks for sell orders that can be matched based on price, handles partial and full fills,
     * and removes fully filled orders from the order book.
     *
     * @param buyOrder the buy order to be matched
     */
    private void matchBuyOrder(OffHeapOrder buyOrder) {
        while (true) {
            Map.Entry<Long, Deque<OffHeapOrder>> bestAskEntry = sellSide.firstEntry();
            if (bestAskEntry == null) break;
            long bestAskPrice = bestAskEntry.getKey();
            if (buyOrder.getPrice() < bestAskPrice) {
                break;
            }
            Deque<OffHeapOrder> askQueue = bestAskEntry.getValue();
            while (!askQueue.isEmpty() && buyOrder.getRemainingQuantity() > 0) {
                OffHeapOrder sellOrder = askQueue.peek();
                if (sellOrder == null || sellOrder.getStatus() != OrderStatus.OPEN) {
                    askQueue.poll();
                    continue;
                }
                long fillQty = Math.min(buyOrder.getRemainingQuantity(), sellOrder.getRemainingQuantity());
                long tradePrice = bestAskPrice;

                buyOrder.fill(fillQty);
                sellOrder.fill(fillQty);

                trades.add(new OffHeapTrade(buyOrder.getOrderId(), sellOrder.getOrderId(), tradePrice, fillQty));

                if (sellOrder.getStatus() != OrderStatus.OPEN) {
                    askQueue.poll();
                    sellOrdersById.remove(sellOrder.getOrderId());
                }
            }
            if (askQueue.isEmpty()) {
                sellSide.remove(bestAskPrice);
            }
            if (buyOrder.getRemainingQuantity() == 0) {
                buyOrdersById.remove(buyOrder.getOrderId());
                break;
            }
        }

        if (buyOrder.getRemainingQuantity() > 0) {
            buySide.computeIfAbsent(buyOrder.getPrice(), k -> new ArrayDeque<>()).offer(buyOrder);
        }
    }

    /**
     * Matches a sell order against the buy side of the order book.
     * It checks for buy orders that can be matched based on price, handles partial and full fills,
     * and removes fully filled orders from the order book.
     *
     * @param sellOrder the sell order to be matched
     */
    private void matchSellOrder(OffHeapOrder sellOrder) {
        while (true) {
            Map.Entry<Long, Deque<OffHeapOrder>> bestBidEntry = buySide.firstEntry();
            if (bestBidEntry == null) break;
            long bestBidPrice = bestBidEntry.getKey();
            if (sellOrder.getPrice() > bestBidPrice) {
                break;
            }
            Deque<OffHeapOrder> bidQueue = bestBidEntry.getValue();
            while (!bidQueue.isEmpty() && sellOrder.getRemainingQuantity() > 0) {
                OffHeapOrder buyOrder = bidQueue.peek();
                if (buyOrder == null || buyOrder.getStatus() != OrderStatus.OPEN) {
                    bidQueue.poll();
                    continue;
                }
                long fillQty = Math.min(sellOrder.getRemainingQuantity(), buyOrder.getRemainingQuantity());
                long tradePrice = bestBidPrice;

                sellOrder.fill(fillQty);
                buyOrder.fill(fillQty);

                trades.add(new OffHeapTrade(buyOrder.getOrderId(), sellOrder.getOrderId(), tradePrice, fillQty));

                if (buyOrder.getStatus() != OrderStatus.OPEN) {
                    bidQueue.poll();
                    buyOrdersById.remove(buyOrder.getOrderId());
                }
            }
            if (bidQueue.isEmpty()) {
                buySide.remove(bestBidPrice);
            }
            if (sellOrder.getRemainingQuantity() == 0) {
                sellOrdersById.remove(sellOrder.getOrderId());
                break;
            }
        }

        if (sellOrder.getRemainingQuantity() > 0) {
            sellSide.computeIfAbsent(sellOrder.getPrice(), k -> new ArrayDeque<>()).offer(sellOrder);
        }
    }

    /**
     * Provides access to all trades generated from matching orders.
     *
     * @return the Deque of OffHeapTrade instances
     */
    public Deque<OffHeapTrade> getTrades() {
        return trades;
    }

    /**
     * Retrieves a buy order by its ID for testing and debugging purposes.
     *
     * @param orderId the ID of the buy order
     * @return the OffHeapOrder instance if found, otherwise null
     */
    public OffHeapOrder getBuyOrderById(String orderId) {
        return buyOrdersById.get(orderId);
    }

    /**
     * Retrieves a sell order by its ID for testing and debugging purposes.
     *
     * @param orderId the ID of the sell order
     * @return the OffHeapOrder instance if found, otherwise null
     */
    public OffHeapOrder getSellOrderById(String orderId) {
        return sellOrdersById.get(orderId);
    }

    // Optional accessors for debugging
    public NavigableMap<Long, Deque<OffHeapOrder>> getBuySide() {
        return buySide;
    }

    public NavigableMap<Long, Deque<OffHeapOrder>> getSellSide() {
        return sellSide;
    }
}