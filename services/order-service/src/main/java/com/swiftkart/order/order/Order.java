package com.swiftkart.order.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private String id;
    /** Optimistic locking: concurrent Kafka consumers and API calls can't silently overwrite each other. */
    @Version
    private long version;
    private String customerId;
    private String customerName;
    private String storeId;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    @Column(columnDefinition = "text")
    private String lines;
    private int itemCount;
    private BigDecimal itemTotal;
    private BigDecimal deliveryFee;
    private BigDecimal total;
    private double customerLat;
    private double customerLon;
    private String address;
    private double distanceKm;
    private int promisedMinutes;
    private Instant promisedBy;
    /** Live estimate, updated by dispatch as the rider moves. */
    private Instant expectedBy;
    private Instant reservationExpiresAt;
    private String paymentId;
    private String riderId;
    private String riderName;
    private String batchedWith;
    private String cancelReason;
    private String source;
    private Instant placedAt;
    private Instant paidAt;
    private Instant packingAt;
    private Instant packedAt;
    private Instant riderAssignedAt;
    private Instant pickedUpAt;
    private Instant deliveredAt;
    private Instant closedAt;

    protected Order() {
    }

    public Order(String id, String customerId, String customerName, String storeId, String lines, int itemCount,
                 BigDecimal itemTotal, BigDecimal deliveryFee, double customerLat, double customerLon, String address,
                 double distanceKm, int promisedMinutes, Instant promisedBy, Instant reservationExpiresAt,
                 String source) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.storeId = storeId;
        this.status = OrderStatus.PAYMENT_PENDING;
        this.lines = lines;
        this.itemCount = itemCount;
        this.itemTotal = itemTotal;
        this.deliveryFee = deliveryFee;
        this.total = itemTotal.add(deliveryFee);
        this.customerLat = customerLat;
        this.customerLon = customerLon;
        this.address = address;
        this.distanceKm = distanceKm;
        this.promisedMinutes = promisedMinutes;
        this.promisedBy = promisedBy;
        this.expectedBy = promisedBy;
        this.reservationExpiresAt = reservationExpiresAt;
        this.source = source;
        this.placedAt = Instant.now();
    }

    /** Moves forward only; returns false if the transition is stale or illegal. */
    public boolean advance(OrderStatus next) {
        if (!status.canAdvanceTo(next)) return false;
        status = next;
        Instant now = Instant.now();
        switch (next) {
            case CONFIRMED -> {
                // The delivery clock starts when the order is confirmed, not while the customer
                // is still in their UPI app.
                paidAt = now;
                Duration promise = Duration.between(placedAt, promisedBy);
                promisedBy = now.plus(promise);
                expectedBy = promisedBy;
            }
            case PACKING -> packingAt = now;
            case PACKED -> packedAt = now;
            case OUT_FOR_DELIVERY -> pickedUpAt = now;
            case DELIVERED -> { deliveredAt = now; closedAt = now; }
            case CANCELLED, PAYMENT_FAILED, EXPIRED -> closedAt = now;
            default -> { }
        }
        return true;
    }

    public void assignRider(String riderId, String riderName, String batchedWith) {
        this.riderId = riderId;
        this.riderName = riderName;
        this.batchedWith = batchedWith;
        this.riderAssignedAt = Instant.now();
    }

    public void cancel(OrderStatus terminal, String reason) {
        if (advance(terminal)) this.cancelReason = reason;
    }

    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public void setExpectedBy(Instant expectedBy) { this.expectedBy = expectedBy; }
    public Instant getExpectedBy() { return expectedBy; }

    public String getId() { return id; }
    public long getVersion() { return version; }
    public String getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getStoreId() { return storeId; }
    public OrderStatus getStatus() { return status; }
    public String getLines() { return lines; }
    public int getItemCount() { return itemCount; }
    public BigDecimal getItemTotal() { return itemTotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getTotal() { return total; }
    public double getCustomerLat() { return customerLat; }
    public double getCustomerLon() { return customerLon; }
    public String getAddress() { return address; }
    public double getDistanceKm() { return distanceKm; }
    public int getPromisedMinutes() { return promisedMinutes; }
    public Instant getPromisedBy() { return promisedBy; }
    public Instant getReservationExpiresAt() { return reservationExpiresAt; }
    public String getPaymentId() { return paymentId; }
    public String getRiderId() { return riderId; }
    public String getRiderName() { return riderName; }
    public String getBatchedWith() { return batchedWith; }
    public String getCancelReason() { return cancelReason; }
    public String getSource() { return source; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getPackingAt() { return packingAt; }
    public Instant getPackedAt() { return packedAt; }
    public Instant getRiderAssignedAt() { return riderAssignedAt; }
    public Instant getPickedUpAt() { return pickedUpAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getClosedAt() { return closedAt; }
}
