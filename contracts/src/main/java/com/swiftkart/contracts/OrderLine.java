package com.swiftkart.contracts;

import java.math.BigDecimal;

public record OrderLine(String sku, String name, int qty, BigDecimal unitPrice) {
}
