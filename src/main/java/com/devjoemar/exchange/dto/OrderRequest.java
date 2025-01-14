package com.devjoemar.exchange.dto;

import com.devjoemar.exchange.domain.OrderType;

/**
 * A simple DTO for incoming JSON requests to POST /orders.
 *
 * Example JSON:
 * {
 *   "orderId": "B123",
 *   "orderType": "BUY",
 *   "price": 10100,
 *   "quantity": 5
 * }
 */
public class OrderRequest {
    private String orderId;
    private OrderType orderType;
    private long price;
    private long quantity;

    // Getters and setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
}
