package com.ecommerce.common.cache;

/**
 * Centralized cache name constants used across microservices.
 * Ensures consistent naming for Redis keys and simplifies eviction configuration.
 */
public final class CacheNames {

    private CacheNames() {
    }

    public static final String PRODUCTS = "products";
    public static final String PRODUCT_BY_ID = "productById";
    public static final String USER_BY_ID = "userById";
    public static final String INVENTORY_BY_PRODUCT = "inventoryByProduct";
}
