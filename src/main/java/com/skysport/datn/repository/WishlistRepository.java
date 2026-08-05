package com.skysport.datn.repository;

import com.skysport.datn.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Integer> {
    Wishlist findByCustomer_Id(Integer customerId);
}