package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.inventory.service.InventoryService.InventoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable UUID productId) {
        log.info("GET /api/inventory/{}", productId);
        InventoryResponse response = inventoryService.getInventory(productId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{productId}")
    public ResponseEntity<InventoryResponse> updateStock(@PathVariable UUID productId,
                                                          @RequestParam Integer quantity) {
        log.info("PUT /api/inventory/{} quantity={}", productId, quantity);
        InventoryResponse response = inventoryService.updateStock(productId, quantity);
        return ResponseEntity.ok(response);
    }

}
