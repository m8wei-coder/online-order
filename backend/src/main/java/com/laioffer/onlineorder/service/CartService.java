package com.laioffer.onlineorder.service;

import com.laioffer.onlineorder.entity.CartEntity;
import com.laioffer.onlineorder.entity.MenuItemEntity;
import com.laioffer.onlineorder.entity.OrderItemEntity;
import com.laioffer.onlineorder.model.CartDto;
import com.laioffer.onlineorder.model.OrderItemDto;
import com.laioffer.onlineorder.repository.CartRepository;
import com.laioffer.onlineorder.repository.MenuItemRepository;
import com.laioffer.onlineorder.repository.OrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderItemRepository orderItemRepository;
    public CartService(
            CartRepository cartRepository,
            MenuItemRepository menuItemRepository,
            OrderItemRepository orderItemRepository) {
        this.cartRepository = cartRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderItemRepository = orderItemRepository;
    }

    // 保护数据完整性(temp data)
    // 用于多个写操作
    @Transactional
    public void addMenuItemToCart(long customerId, long menuItemId) {
        CartEntity cart = cartRepository.getByCustomerIdForUpdate(customerId);
        MenuItemEntity menuItem = menuItemRepository.findById(menuItemId).get();
        OrderItemEntity orderItem = orderItemRepository.findByCartIdAndMenuItemId(cart.id(), menuItem.id());

        Long orderItemId;
        int quantity;

        if (orderItem == null) {
            orderItemId = null;
            quantity = 1;
        } else {
            orderItemId = orderItem.id();
            quantity = orderItem.quantity() + 1;
        }
        // insert(null) or update
        OrderItemEntity newOrderItem = new OrderItemEntity(orderItemId, menuItemId, cart.id(), menuItem.price(), quantity);
        orderItemRepository.save(newOrderItem);
        // if throw exception, the statement above will roll back
        cartRepository.updateTotalPrice(cart.id(), cart.totalPrice() + menuItem.price());
    }

    public CartDto getCart(Long customerId) {
        CartEntity cart = cartRepository.getByCustomerId(customerId);
        List<OrderItemDto> orderItemDtos = orderItemRepository.getCartItemsWithMenuDetails(cart.id()).stream()
                .map(item -> new OrderItemDto(item.orderItemId(), item.menuItemId(), item.restaurantId(),
                        item.price(), item.quantity(), item.menuItemName(), item.menuItemDescription(),
                        item.menuItemImageUrl()))
                .toList();
        return new CartDto(cart, orderItemDtos);
    }

    @Transactional
    public void clearCart(Long customerId) {
        CartEntity cartEntity = cartRepository.getByCustomerIdForUpdate(customerId);
        orderItemRepository.deleteByCartId(cartEntity.id());
        cartRepository.updateTotalPrice(cartEntity.id(), 0.0);
    }

}

