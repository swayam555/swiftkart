package com.swiftkart.order.api;

import com.swiftkart.contracts.ReservationRequest;
import com.swiftkart.order.config.AppProperties;
import com.swiftkart.order.live.LiveUpdates;
import com.swiftkart.order.order.Order;
import com.swiftkart.order.order.OrderRepository;
import com.swiftkart.order.order.OrderService;
import com.swiftkart.order.order.OrderStatus;
import com.swiftkart.order.order.OrderView;
import com.swiftkart.order.sim.FlashSale;
import com.swiftkart.order.sim.LoadGenerator;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Operations console API. The gateway only lets OPS-role tokens reach {@code /api/ops/**}. */
@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final OrderRepository orders;
    private final OrderService service;
    private final LoadGenerator load;
    private final FlashSale flash;
    private final LiveUpdates live;
    private final AppProperties props;

    public OpsController(OrderRepository orders, OrderService service, LoadGenerator load, FlashSale flash,
                         LiveUpdates live, AppProperties props) {
        this.orders = orders;
        this.service = service;
        this.load = load;
        this.flash = flash;
        this.live = live;
        this.props = props;
    }

    /** KPIs over the last 15 real minutes (about 90 minutes of simulated time at 6x). */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Instant since = Instant.now().minus(Duration.ofMinutes(15));
        List<Order> recent = orders.findByPlacedAtAfter(since);
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        Arrays.stream(OrderStatus.values()).forEach(s -> byStatus.put(s, 0L));
        recent.forEach(o -> byStatus.merge(o.getStatus(), 1L, Long::sum));

        List<Order> delivered = recent.stream().filter(o -> o.getDeliveredAt() != null && o.getPaidAt() != null).toList();
        double avgMinutes = delivered.stream()
                .mapToDouble(o -> props.simulatedMinutes(Duration.between(o.getPaidAt(), o.getDeliveredAt())))
                .average().orElse(0);
        long onTime = delivered.stream().filter(o -> !o.getDeliveredAt().isAfter(o.getPromisedBy())).count();
        BigDecimal gmv = recent.stream().filter(o -> o.getPaidAt() != null && o.getStatus() != OrderStatus.CANCELLED)
                .map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        double avgPromise = recent.stream().mapToInt(Order::getPromisedMinutes).average().orElse(0);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("windowMinutes", 15);
        body.put("timeScale", props.timeScale());
        body.put("placed", recent.size());
        body.put("byStatus", byStatus);
        body.put("delivered", delivered.size());
        body.put("avgDeliveryMinutes", Math.round(avgMinutes * 10) / 10.0);
        body.put("avgPromisedMinutes", Math.round(avgPromise * 10) / 10.0);
        body.put("onTimeRate", delivered.isEmpty() ? null : Math.round(1000.0 * onTime / delivered.size()) / 10.0);
        body.put("gmv", gmv);
        body.put("paymentFailures", byStatus.get(OrderStatus.PAYMENT_FAILED));
        body.put("cancelled", byStatus.get(OrderStatus.CANCELLED));
        body.put("expired", byStatus.get(OrderStatus.EXPIRED));
        body.put("activeByStore", recent.stream().filter(o -> o.getStatus().isActive())
                .collect(Collectors.groupingBy(Order::getStoreId, Collectors.counting())));
        body.put("simulator", load.status());
        return body;
    }

    @GetMapping("/orders")
    public List<OrderView> active(@RequestParam(defaultValue = "true") boolean activeOnly,
                                  @RequestParam(defaultValue = "100") int limit) {
        List<OrderStatus> statuses = activeOnly
                ? Arrays.stream(OrderStatus.values()).filter(OrderStatus::isActive).toList()
                : Arrays.asList(OrderStatus.values());
        return orders.findByStatusInOrderByPlacedAtDesc(statuses, PageRequest.of(0, Math.min(limit, 300))).stream()
                .map(o -> service.toView(o, false)).toList();
    }

    @GetMapping(path = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        return live.subscribeOps();
    }

    @PostMapping("/simulator/start")
    public LoadGenerator.Status start(@RequestBody(required = false) StartLoad req) {
        StartLoad r = req == null ? new StartLoad(20.0, null) : req;
        return load.start(r.ordersPerMinute() == null ? 20 : r.ordersPerMinute(), r.city());
    }

    @PostMapping("/simulator/stop")
    public LoadGenerator.Status stop() {
        return load.stop();
    }

    @PostMapping("/flash-sale")
    public FlashSale.Result flashSale(@RequestBody FlashRequest req) throws Exception {
        return flash.run(req.storeId(), req.units(), req.buyers(),
                req.mode() == null ? ReservationRequest.Mode.ATOMIC : req.mode());
    }

    public record StartLoad(Double ordersPerMinute, String city) {
    }

    public record FlashRequest(String storeId, int units, int buyers, ReservationRequest.Mode mode) {
    }
}
