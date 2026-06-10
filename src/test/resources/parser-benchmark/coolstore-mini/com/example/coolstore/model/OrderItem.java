package com.example.coolstore.model;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "ORDER_ITEMS")
public class OrderItem implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "ID")
    private Long id;

    private String productId;
    private int quantity;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
