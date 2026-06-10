package com.example.coolstore.rest;

import com.example.coolstore.model.Product;
import com.example.coolstore.service.ProductService;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RequestScoped
@Path("/products")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProductEndpoint {

    @Inject
    private ProductService productService;

    @GET
    @Path("/")
    public List<Product> listAll() {
        return productService.getProducts();
    }

    @GET
    @Path("/{itemId}")
    public Product getProduct(@PathParam("itemId") String itemId) {
        return productService.getProductByItemId(itemId);
    }
}
