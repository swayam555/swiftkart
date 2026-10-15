package com.swiftkart.dispatch.fleet;

public class DispatchOrder {

    public enum Status { QUEUED, ASSIGNED, PICKED_UP, DELIVERED, CANCELLED }

    public String orderId;
    public String storeId;
    public double lat;
    public double lon;
    public long queuedAt;
    public long promisedBy;
    public Status status = Status.QUEUED;
    public boolean packed;
    public String riderId;
    public String batchedWith;
    public long etaAt;
    public long closedAt;

    public DispatchOrder() {
    }

    public DispatchOrder(String orderId, String storeId, double lat, double lon, long promisedBy) {
        this.orderId = orderId;
        this.storeId = storeId;
        this.lat = lat;
        this.lon = lon;
        this.promisedBy = promisedBy;
        this.queuedAt = System.currentTimeMillis();
    }
}
