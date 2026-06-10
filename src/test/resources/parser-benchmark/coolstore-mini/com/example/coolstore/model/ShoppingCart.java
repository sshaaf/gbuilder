package com.example.coolstore.model;

import javax.enterprise.context.Dependent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Dependent
public class ShoppingCart implements Serializable {

    private final List<ShoppingCartItem> items = new ArrayList<>();

    public List<ShoppingCartItem> getItems() {
        return items;
    }

    public void addItem(ShoppingCartItem item) {
        items.add(item);
    }
}
