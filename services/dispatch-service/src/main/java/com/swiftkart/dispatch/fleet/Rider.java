package com.swiftkart.dispatch.fleet;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/** Mutable rider state, owned by the leader's simulation loop and snapshotted to Redis. */
public class Rider {

    public enum Status { IDLE, TO_STORE, AT_STORE, DELIVERING, AT_DROP, RETURNING }

    public String id;
    public String name;
    public String storeId;
    public Status status = Status.IDLE;
    public double lat;
    public double lon;
    /** Orders this rider is carrying or about to collect (at most two: batching). */
    public List<String> orders = new ArrayList<>();
    /** Drops still to make, in visiting order. */
    public List<String> route = new ArrayList<>();
    public long busyUntil;
    public long idleSince;
    public long deliveries;
    public long batches;

    public Rider() {
    }

    public Rider(String id, String name, String storeId, double lat, double lon) {
        this.id = id;
        this.name = name;
        this.storeId = storeId;
        this.lat = lat;
        this.lon = lon;
        this.idleSince = System.currentTimeMillis();
    }

    @JsonIgnore
    public boolean isFree() {
        return status == Status.IDLE || status == Status.RETURNING;
    }

    /** Assigned but not yet left the store: a second order could still be added to this trip. */
    @JsonIgnore
    public boolean canBatch() {
        return (status == Status.TO_STORE || status == Status.AT_STORE) && orders.size() == 1;
    }
}
