package com.ecommerce.order.repository;

import com.ecommerce.order.entity.ReturnSaga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnSagaRepository extends JpaRepository<ReturnSaga, UUID> {

    Optional<ReturnSaga> findByOrderId(UUID orderId);

    boolean existsByOrderIdAndStatusNot(UUID orderId, String status);
}
