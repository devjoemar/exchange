package com.devjoemar.exchange.domain;

import java.util.Objects;

/**
 * Represents a BUY or SELL order, storing price/quantity as scaled longs 
 * to minimize GC overhead.
 *
 * <p><strong>Rationale:</strong></p>
 * <ul>
 *   <li>Avoids BigDecimal for microsecond-level performance.</li>
 *   <li>Price can be stored in "cents" or "ticks." e.g. price=10050 => $100.50.</li>
 * </ul>
 */
public class OffHeapOrder {

    private final String orderId;
    private final OrderType orderType;
    private final long price;         // must be > 0
    private long remainingQuantity;   // must be > 0

    private OrderStatus status;

    public OffHeapOrder(String orderId, OrderType orderType,
                        long price, long quantity) {
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.orderType = Objects.requireNonNull(orderType, "orderType cannot be null");
        if (price <= 0) {
            throw new IllegalArgumentException("price must be > 0");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        this.price = price;
        this.remainingQuantity = quantity;
        this.status = OrderStatus.OPEN;
    }

    public void fill(long fillQty) {
        if (fillQty <= 0) {
            throw new IllegalArgumentException("fillQty must be > 0");
        }
        remainingQuantity -= fillQty;
        if (remainingQuantity <= 0) {
            status = OrderStatus.FILLED;
            remainingQuantity = 0;
        } else {
            status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void cancel() {
        status = OrderStatus.CANCELED;
        remainingQuantity = 0;
    }

    // getters
    public String getOrderId() { return orderId; }
    public OrderType getOrderType() { return orderType; }
    public long getPrice() { return price; }
    public long getRemainingQuantity() { return remainingQuantity; }
    public OrderStatus getStatus() { return status; }
}
