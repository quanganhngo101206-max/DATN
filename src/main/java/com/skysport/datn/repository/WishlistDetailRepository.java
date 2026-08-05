package com.skysport.datn.repository;

import com.skysport.datn.entity.WishlistDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WishlistDetailRepository extends JpaRepository<WishlistDetail, Integer> {
    List<WishlistDetail> findByWishlist_Id(Integer wishlistId);
    WishlistDetail findByWishlist_IdAndProduct_Id(Integer wishlistId, Integer productId);
    void deleteByWishlist_IdAndProduct_Id(Integer wishlistId, Integer productId);
    int countByWishlist_Id(Integer wishlistId);
}