package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    Optional<InventoryReservation> findByOrderIdAndProductId(UUID orderId, UUID productId);

    List<InventoryReservation> findByOrderId(UUID orderId);

    boolean existsByOrderIdAndProductIdAndStatus(UUID orderId, UUID productId, String status);

}
