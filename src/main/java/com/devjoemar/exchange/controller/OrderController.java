package com.devjoemar.exchange.controller;

import com.devjoemar.exchange.domain.OffHeapOrder;
import com.devjoemar.exchange.dto.OrderRequest;
import com.devjoemar.exchange.queue.ChronicleQueueOrderPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final ChronicleQueueOrderPublisher publisher;

    public OrderController(ChronicleQueueOrderPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Submits a new order to the matching engine via Chronicle Queue.
     *
     * @param request the order details (price in "ticks" or "cents", quantity > 0)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void submitOrder(@RequestBody OrderRequest request) {
        if (request.getPrice() <= 0 || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Price and quantity must be > 0");
        }
        if (request.getOrderId() == null || request.getOrderType() == null) {
            throw new IllegalArgumentException("orderId and orderType must not be null");
        }

        OffHeapOrder order = new OffHeapOrder(
            request.getOrderId(),
            request.getOrderType(),
            request.getPrice(),
            request.getQuantity()
        );

        // Publish to off-heap queue
        publisher.publishOrder(order);
    }
}
