package com.devjoemar.exchange.domain;

import java.util.Objects;

/**
 * Represents a trade resulting from matching a BUY and SELL order.
 */
public final class OffHeapTrade {
    private final String buyOrderId;
    private final String sellOrderId;
    private final long tradePrice;
    private final long tradeQuantity;

    public OffHeapTrade(String buyOrderId, String sellOrderId,
                        long tradePrice, long tradeQuantity) {
        this.buyOrderId = Objects.requireNonNull(buyOrderId);
        this.sellOrderId = Objects.requireNonNull(sellOrderId);
        if (tradePrice <= 0) {
            throw new IllegalArgumentException("tradePrice must be > 0");
        }
        if (tradeQuantity <= 0) {
            throw new IllegalArgumentException("tradeQuantity must be > 0");
        }
        this.tradePrice = tradePrice;
        this.tradeQuantity = tradeQuantity;
    }

    public String getBuyOrderId() { return buyOrderId; }
    public String getSellOrderId() { return sellOrderId; }
    public long getTradePrice() { return tradePrice; }
    public long getTradeQuantity() { return tradeQuantity; }
}
