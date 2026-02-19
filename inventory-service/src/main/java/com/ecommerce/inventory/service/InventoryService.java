package com.ecommerce.inventory.service;

import com.ecommerce.common.cache.CacheNames;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.entity.InventoryReservation;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.INVENTORY_BY_PRODUCT, key = "#productId")
    public InventoryResponse getInventory(UUID productId) {
        log.info("Fetching inventory for productId={}", productId);

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for productId: " + productId));

        return mapToResponse(inventory);
    }

    @Transactional
    @CacheEvict(value = CacheNames.INVENTORY_BY_PRODUCT, key = "#productId")
    public InventoryResponse updateStock(UUID productId, Integer quantity) {
        log.info("Updating stock for productId={} to quantity={}", productId, quantity);

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> Inventory.builder()
                        .productId(productId)
                        .availableQuantity(0)
                        .reservedQuantity(0)
                        .build());

        inventory.setAvailableQuantity(quantity);
        Inventory saved = inventoryRepository.save(inventory);

        log.info("Stock updated for productId={}, availableQuantity={}", productId, saved.getAvailableQuantity());
        return mapToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheNames.INVENTORY_BY_PRODUCT, key = "#productId")
    public boolean reserveStock(UUID orderId, UUID productId, Integer quantity) {
        log.info("Reserving stock: orderId={}, productId={}, quantity={}", orderId, productId, quantity);

        if (reservationRepository.existsByOrderIdAndProductIdAndStatus(orderId, productId, "RESERVED")) {
            log.info("Reservation already exists for orderId={}, productId={} - idempotent return", orderId, productId);
            return true;
        }

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElse(null);

        if (inventory == null) {
            log.warn("No inventory record found for productId={}", productId);
            return false;
        }

        if (inventory.getAvailableQuantity() < quantity) {
            log.warn("Insufficient stock for productId={}: available={}, requested={}",
                    productId, inventory.getAvailableQuantity(), quantity);
            return false;
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        inventoryRepository.save(inventory);

        InventoryReservation reservation = InventoryReservation.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .status("RESERVED")
                .build();
        reservationRepository.save(reservation);

        log.info("Stock reserved successfully: orderId={}, productId={}, quantity={}", orderId, productId, quantity);
        return true;
    }

    @Transactional
    public void releaseStock(UUID orderId) {
        log.info("Releasing stock for orderId={}", orderId);

        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);

        for (InventoryReservation reservation : reservations) {
            if (!"RESERVED".equals(reservation.getStatus())) {
                continue;
            }

            Inventory inventory = inventoryRepository.findByProductIdWithLock(reservation.getProductId())
                    .orElse(null);

            if (inventory != null) {
                inventory.setAvailableQuantity(inventory.getAvailableQuantity() + reservation.getQuantity());
                inventory.setReservedQuantity(inventory.getReservedQuantity() - reservation.getQuantity());
                inventoryRepository.save(inventory);
            }

            reservation.setStatus("RELEASED");
            reservationRepository.save(reservation);

            log.info("Released reservation: orderId={}, productId={}, quantity={}",
                    orderId, reservation.getProductId(), reservation.getQuantity());
        }
    }

    /**
     * Restores stock from a confirmed reservation back to available inventory.
     * Used by the return/refund saga to undo completed orders.
     */
    @Transactional
    @CacheEvict(value = CacheNames.INVENTORY_BY_PRODUCT, key = "#productId")
    public void restoreStock(UUID orderId, UUID productId, Integer quantity) {
        log.info("Restoring stock: orderId={}, productId={}, quantity={}", orderId, productId, quantity);

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for productId: " + productId));

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - quantity));
        inventoryRepository.save(inventory);

        log.info("Stock restored: productId={}, newAvailable={}", productId, inventory.getAvailableQuantity());
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(inventory.getProductId())
                .availableQuantity(inventory.getAvailableQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }

    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class InventoryResponse {
        private UUID id;
        private UUID productId;
        private Integer availableQuantity;
        private Integer reservedQuantity;
        private java.time.LocalDateTime updatedAt;
    }
}
