package com.swiftkart.order.api;

import com.swiftkart.contracts.ReservationRequest;
import com.swiftkart.order.live.LiveUpdates;
import com.swiftkart.order.order.OrderRepository;
import com.swiftkart.order.order.OrderService;
import com.swiftkart.order.order.OrderView;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;
    private final OrderRepository orders;
    private final LiveUpdates live;

    public OrderController(OrderService service, OrderRepository orders, LiveUpdates live) {
        this.service = service;
        this.orders = orders;
        this.live = live;
    }

    @PostConstruct
    void wire() {
        live.setRenderer(service::view);
    }

    @PostMapping
    public ResponseEntity<OrderView> place(@RequestBody OrderService.PlaceOrder req,
                                           @RequestHeader("X-User-Id") String customerId,
                                           @RequestHeader(value = "X-User-Name", required = false) String name,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(req, customerId, name, key, ReservationRequest.Mode.ATOMIC));
    }

    @GetMapping
    public List<OrderView> mine(@RequestHeader("X-User-Id") String customerId) {
        return orders.findByCustomerIdOrderByPlacedAtDesc(customerId, PageRequest.of(0, 20)).stream()
                .map(o -> service.toView(o, false)).toList();
    }

    @GetMapping("/{id}")
    public OrderView one(@PathVariable String id, @RequestHeader("X-User-Id") String customerId,
                         @RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
        OrderView v = service.view(id);
        if (!"OPS".equals(role) && !v.customerId().equals(customerId)) throw new java.util.NoSuchElementException("Order not found");
        return v;
    }

    @PostMapping("/{id}/pay")
    public OrderView pay(@PathVariable String id, @RequestHeader("X-User-Id") String customerId,
                         @RequestBody(required = false) PayRequest req) {
        PayRequest r = req == null ? new PayRequest("UPI", false) : req;
        return service.pay(id, customerId, r.method() == null ? "UPI" : r.method(),
                Boolean.TRUE.equals(r.simulateFailure()));
    }

    @PostMapping("/{id}/cancel")
    public OrderView cancel(@PathVariable String id, @RequestHeader("X-User-Id") String customerId) {
        return service.cancel(id, customerId, "Cancelled by customer");
    }

    @GetMapping(path = "/{id}/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable String id, @RequestHeader("X-User-Id") String customerId,
                             @RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
        one(id, customerId, role); // ownership check
        return live.subscribe(id);
    }

    public record PayRequest(String method, Boolean simulateFailure) {
    }
}
