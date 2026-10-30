package com.laioffer.onlineorder.repository;

import com.laioffer.onlineorder.entity.OrderItemEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;


public interface OrderItemRepository extends ListCrudRepository<OrderItemEntity, Long> {
    List<OrderItemEntity> getAllByCartId(Long cartId);

    @Query("SELECT oi.id AS order_item_id, oi.menu_item_id AS menu_item_id, m.restaurant_id AS restaurant_id, oi.price AS price, oi.quantity AS quantity, m.name AS menu_item_name, m.description AS menu_item_description, m.image_url AS menu_item_image_url FROM order_items oi JOIN menu_items m ON m.id = oi.menu_item_id WHERE oi.cart_id = :cartId")
    List<CartItemReadRow> getCartItemsWithMenuDetails(@Param("cartId") Long cartId);

    OrderItemEntity findByCartIdAndMenuItemId(Long cartId, Long menuItemId);

    @Modifying
    @Query("DELETE FROM order_items WHERE cart_id = :cartId")
    void deleteByCartId(Long cartId);
}

