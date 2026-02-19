package com.ecommerce.product.mapper;

import com.ecommerce.common.dto.ProductRequest;
import com.ecommerce.common.dto.ProductResponse;
import com.ecommerce.product.entity.Product;
import org.springframework.stereotype.Component;

/**
 * Manual mapper replacing MapStruct since product IDs changed from UUID to String (MongoDB).
 * MapStruct is no longer needed for this service after the MongoDB migration.
 */
@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .sku(product.getSku())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .imageUrl(product.getImageUrl())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public Product toEntity(ProductRequest request) {
        return Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .sku(request.getSku())
                .imageUrl(request.getImageUrl())
                .build();
    }

    public void updateEntity(ProductRequest request, Product product) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setSku(request.getSku());
        product.setImageUrl(request.getImageUrl());
    }
}
