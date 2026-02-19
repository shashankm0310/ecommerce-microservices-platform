package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("{ $and: [ " +
           "  { $or: [ { $expr: { $eq: [?0, null] } }, { 'name': { $regex: ?0, $options: 'i' } } ] }, " +
           "  { $or: [ { $expr: { $eq: [?1, null] } }, { 'category.name': { $regex: ?1, $options: 'i' } } ] } " +
           "] }")
    Page<Product> findByFilters(String name, String categoryName, Pageable pageable);
}
