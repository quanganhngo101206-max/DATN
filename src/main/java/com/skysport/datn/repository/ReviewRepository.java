package com.skysport.datn.repository;

import com.skysport.datn.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {

    List<Review> findByProduct_IdOrderByCreatedDateDesc(Integer productId);

    Review findByProduct_IdAndCustomer_Id(Integer productId, Integer customerId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Double findAverageRatingByProductId(@Param("productId") Integer productId);

    Integer countByProduct_Id(Integer productId);
}