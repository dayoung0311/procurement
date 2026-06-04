package com.bbd.procurement.vendor.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByCode(String code);

    boolean existsByCode(String code);

}
