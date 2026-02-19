package com.ecommerce.product.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MongoDB document representing a product in the catalog.
 * Migrated from PostgreSQL/JPA to MongoDB for polyglot persistence demonstration.
 *
 * Key differences from JPA version:
 * - @Document instead of @Entity/@Table
 * - String ID instead of UUID (MongoDB ObjectId-compatible)
 * - @Version for optimistic locking (same concept, different provider)
 * - @Indexed for explicit index control (no DDL auto-migration)
 */
@Document(collection = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    private String id;

    @TextIndexed
    private String name;

    private String description;

    private BigDecimal price;

    @Indexed(unique = true)
    private String sku;

    private String imageUrl;

    private Category category;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
