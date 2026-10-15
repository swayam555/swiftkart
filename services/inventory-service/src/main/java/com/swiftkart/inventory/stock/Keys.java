package com.swiftkart.inventory.stock;

/**
 * Redis key layout. The store id in braces is a Redis Cluster hash tag: every key for one store
 * hashes to the same slot, which is what lets a multi-key Lua script stay atomic on a cluster.
 */
public final class Keys {

    private Keys() {
    }

    public static String stock(String store, String sku) {
        return "stock:{" + store + "}:" + sku;
    }

    public static String reserved(String store, String sku) {
        return "reserved:{" + store + "}:" + sku;
    }

    public static String reservation(String store, String orderId) {
        return "resv:{" + store + "}:" + orderId;
    }

    public static String expiries(String store) {
        return "resv-exp:{" + store + "}";
    }

    public static String pickers(String store) {
        return "pickers:{" + store + "}";
    }

    public static String sold(String store, String sku, long minute) {
        return "sold:{" + store + "}:" + sku + ":" + minute;
    }

    public static final String STORES_GEO = "stores:geo";
    public static final String DUE_EVENTS = "store:due-events";
}
