package com.example.coolstore.service;

import com.example.coolstore.model.Order;
import com.example.coolstore.utils.Transformers;

import javax.inject.Inject;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

public class InventoryNotificationMDB implements MessageListener {

    @Inject
    private CatalogService catalogService;

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage textMessage) {
                String body = textMessage.getBody(String.class);
                Order order = Transformers.jsonToOrder(body);
                order.getItemList().forEach(item ->
                        catalogService.getProductByItemId(item.getProductId()));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
