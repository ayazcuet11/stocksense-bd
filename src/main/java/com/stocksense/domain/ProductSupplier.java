package com.stocksense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "product_suppliers")
public class ProductSupplier {

    @EmbeddedId
    private ProductSupplierId id;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
}
