package com.ecommerce.notification.repository;

import com.ecommerce.notification.entity.OrderSagaView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderSagaViewRepository extends JpaRepository<OrderSagaView, UUID> {
}
