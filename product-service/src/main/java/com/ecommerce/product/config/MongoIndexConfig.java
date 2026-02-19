package com.ecommerce.product.config;

import com.ecommerce.product.entity.Product;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;

/**
 * Programmatic MongoDB index creation.
 * Ensures indexes exist on startup for query performance and uniqueness constraints.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        // Unique index on SKU
        mongoTemplate.indexOps(Product.class)
                .ensureIndex(new Index().on("sku", Sort.Direction.ASC).unique());

        // Index on category name for filtered queries
        mongoTemplate.indexOps(Product.class)
                .ensureIndex(new Index().on("category.name", Sort.Direction.ASC));

        // Text index on product name for full-text search
        mongoTemplate.indexOps(Product.class)
                .ensureIndex(new TextIndexDefinition.TextIndexDefinitionBuilder()
                        .onField("name")
                        .build());

        log.info("MongoDB indexes created for products collection");
    }
}
