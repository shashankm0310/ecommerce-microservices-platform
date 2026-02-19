package com.ecommerce.product.service;

import com.ecommerce.common.cache.CacheNames;
import com.ecommerce.common.dto.ProductRequest;
import com.ecommerce.common.dto.ProductResponse;
import com.ecommerce.common.exception.DuplicateResourceException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public Page<ProductResponse> getAllProducts(String name, String categoryName, Pageable pageable) {
        return productRepository.findByFilters(name, categoryName, pageable)
                .map(productMapper::toResponse);
    }

    @Cacheable(value = CacheNames.PRODUCT_BY_ID, key = "#id")
    public ProductResponse getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return productMapper.toResponse(product);
    }

    @Caching(evict = {
        @CacheEvict(value = CacheNames.PRODUCTS, allEntries = true)
    })
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateResourceException("Product", "sku", request.getSku());
        }

        Product product = productMapper.toEntity(request);

        if (request.getCategoryName() != null && !request.getCategoryName().isBlank()) {
            Category category = categoryRepository.findByNameIgnoreCase(request.getCategoryName())
                    .orElseGet(() -> categoryRepository.save(
                            Category.builder().name(request.getCategoryName()).build()));
            product.setCategory(category);
        }

        Product saved = productRepository.save(product);
        log.info("Created product: {} (SKU: {})", saved.getId(), saved.getSku());
        return productMapper.toResponse(saved);
    }

    @Caching(evict = {
        @CacheEvict(value = CacheNames.PRODUCT_BY_ID, key = "#id"),
        @CacheEvict(value = CacheNames.PRODUCTS, allEntries = true)
    })
    public ProductResponse updateProduct(String id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        productRepository.findBySku(request.getSku())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Product", "sku", request.getSku());
                });

        productMapper.updateEntity(request, product);

        if (request.getCategoryName() != null && !request.getCategoryName().isBlank()) {
            Category category = categoryRepository.findByNameIgnoreCase(request.getCategoryName())
                    .orElseGet(() -> categoryRepository.save(
                            Category.builder().name(request.getCategoryName()).build()));
            product.setCategory(category);
        }

        Product saved = productRepository.save(product);
        log.info("Updated product: {}", saved.getId());
        return productMapper.toResponse(saved);
    }

    @Caching(evict = {
        @CacheEvict(value = CacheNames.PRODUCT_BY_ID, key = "#id"),
        @CacheEvict(value = CacheNames.PRODUCTS, allEntries = true)
    })
    public void deleteProduct(String id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product", "id", id);
        }
        productRepository.deleteById(id);
        log.info("Deleted product: {}", id);
    }
}
