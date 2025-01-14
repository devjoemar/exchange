package com.devjoemar.exchange.engine;

import com.devjoemar.exchange.domain.OffHeapOrder;
import com.devjoemar.exchange.domain.OffHeapTrade;
import com.devjoemar.exchange.domain.OrderStatus;
import com.devjoemar.exchange.domain.OrderType;

import java.util.*;

/**
 * A limit order book that stores BUY orders in descending order of price 
 * and SELL orders in ascending order, matching them as they cross.
 *
 * <p><strong>Rationale:</strong></p>
 * <ul>
 *   <li>O(log n) insertion using TreeMap keys for price, plus FIFO queues for time priority.</li>
 *   <li>Generates {@link OffHeapTrade} objects on match events.</li>
 * </ul>
 */
public class OffHeapOrderBook {

    private final NavigableMap<Long, Deque<OffHeapOrder>> buySide;  // descending
    private final NavigableMap<Long, Deque<OffHeapOrder>> sellSide; // ascending
    private final Deque<OffHeapTrade> trades;

    public OffHeapOrderBook() {
        this.buySide = new TreeMap<>(Comparator.reverseOrder());
        this.sellSide = new TreeMap<>();
        this.trades = new ArrayDeque<>();
    }

    /**
     * Processes an incoming order, attempting to match it 
     * with opposing orders if price conditions are met.
     *
     * @param order the new buy or sell order
     */
    public void processOrder(OffHeapOrder order) {
        if (order.getOrderType() == OrderType.BUY) {
            matchBuyOrder(order);
        } else {
            matchSellOrder(order);
        }
    }

    private void matchBuyOrder(OffHeapOrder buyOrder) {
        while (true) {
            Map.Entry<Long, Deque<OffHeapOrder>> bestAskEntry = sellSide.firstEntry();
            if (bestAskEntry == null) break;
            long bestAskPrice = bestAskEntry.getKey();
            if (buyOrder.getPrice() < bestAskPrice) {
                // no more matches possible
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
                }
            }
            if (askQueue.isEmpty()) {
                sellSide.remove(bestAskPrice);
            }
            if (buyOrder.getRemainingQuantity() == 0) {
                break;
            }
        }

        // place remainder on buy side
        if (buyOrder.getRemainingQuantity() > 0) {
            buySide.computeIfAbsent(buyOrder.getPrice(), k -> new ArrayDeque<>()).offer(buyOrder);
        }
    }

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
                }
            }
            if (bidQueue.isEmpty()) {
                buySide.remove(bestBidPrice);
            }
            if (sellOrder.getRemainingQuantity() == 0) {
                break;
            }
        }

        // place remainder on sell side
        if (sellOrder.getRemainingQuantity() > 0) {
            sellSide.computeIfAbsent(sellOrder.getPrice(), k -> new ArrayDeque<>()).offer(sellOrder);
        }
    }

    /** Returns all generated trades so far. */
    public Deque<OffHeapTrade> getTrades() {
        return trades;
    }

    // (Optional) Accessors for debugging
    public NavigableMap<Long, Deque<OffHeapOrder>> getBuySide() { return buySide; }
    public NavigableMap<Long, Deque<OffHeapOrder>> getSellSide() { return sellSide; }
}
