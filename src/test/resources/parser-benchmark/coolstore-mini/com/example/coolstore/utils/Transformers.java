package com.example.coolstore.utils;

import com.example.coolstore.model.Order;
import com.example.coolstore.model.Product;
import com.example.coolstore.model.ShoppingCart;

public final class Transformers {

    private Transformers() {
    }

    public static Order jsonToOrder(String json) {
        return new Order();
    }

    public static Product toProduct(ShoppingCart cart) {
        validate(cart);
        return cart.getItems().isEmpty() ? new Product() : cart.getItems().get(0).getProduct();
    }

    private static void validate(ShoppingCart cart) {
        if (cart == null || cart.getItems() == null) {
            throw new IllegalArgumentException("cart required");
        }
    }
}
