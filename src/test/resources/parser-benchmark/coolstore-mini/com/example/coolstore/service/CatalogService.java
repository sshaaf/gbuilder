package com.example.coolstore.service;

import com.example.coolstore.model.Product;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Stateless
public class CatalogService {

    @PersistenceContext
    private EntityManager em;

    public List<Product> getProducts() {
        return em.createQuery("from Product", Product.class).getResultList();
    }

    public Product getProductByItemId(String itemId) {
        List<Product> products = getProducts();
        return products.stream()
                .filter(p -> itemId.equals(p.getItemId()))
                .findFirst()
                .orElse(null);
    }
}
