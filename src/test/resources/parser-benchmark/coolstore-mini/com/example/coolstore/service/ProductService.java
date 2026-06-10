package com.example.coolstore.service;

import com.example.coolstore.model.Product;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;

@Stateless
public class ProductService {

    @Inject
    private CatalogService catalogService;

    public List<Product> getProducts() {
        return catalogService.getProducts();
    }

    public Product getProductByItemId(String itemId) {
        return catalogService.getProductByItemId(itemId);
    }
}
