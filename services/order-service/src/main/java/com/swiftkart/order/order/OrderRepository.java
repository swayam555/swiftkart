package com.swiftkart.order.order;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByCustomerIdOrderByPlacedAtDesc(String customerId, Pageable page);

    List<Order> findByStatusInOrderByPlacedAtDesc(Collection<OrderStatus> statuses, Pageable page);

    long countByStoreIdAndStatusIn(String storeId, Collection<OrderStatus> statuses);

    List<Order> findByPlacedAtAfter(Instant after);

    @Query("select o from Order o where o.deliveredAt > :after")
    List<Order> deliveredSince(@Param("after") Instant after);
}
