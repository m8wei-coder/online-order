package com.laioffer.onlineorder.repository;

public record CartItemReadRow(Long orderItemId, Long menuItemId, Long restaurantId, Double price,
                              Integer quantity, String menuItemName, String menuItemDescription,
                              String menuItemImageUrl) {
}
