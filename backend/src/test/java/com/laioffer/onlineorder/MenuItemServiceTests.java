package com.laioffer.onlineorder;

import com.laioffer.onlineorder.entity.MenuItemEntity;
import com.laioffer.onlineorder.repository.MenuItemRepository;
import com.laioffer.onlineorder.service.MenuItemService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class MenuItemServiceTests {

    @Mock
    private MenuItemRepository menuItemRepository;

    private MenuItemService menuItemService;

    @BeforeEach
    void setup() {
        menuItemService = new MenuItemService(menuItemRepository);
    }

    @Test
    void getMenuItemsByRestaurantId_shouldReturnMenuItems() {
        // Mock data
        long restaurantId = 1L;
        List<MenuItemEntity> menuItems = List.of(
                new MenuItemEntity(1L, restaurantId, "Name1", "Description1", 10.0, "image1"),
                new MenuItemEntity(2L, restaurantId, "Name2", "Description2", 20.0, "image2")
        );

        // Mock repository method calls
        Mockito.when(menuItemRepository.getByRestaurantId(restaurantId)).thenReturn(menuItems);

        // Perform the method under test
        List<MenuItemEntity> results = menuItemService.getMenuItemsByRestaurantId(restaurantId);

        // Verify the expected MenuItemEntity properties
        Assertions.assertEquals(menuItems, results);
    }

    @Test
    void getMenuItemById_shouldReturnMenuItem() {
        // Mock data
        long menuItemId = 1L;
        MenuItemEntity menuItem = new MenuItemEntity(menuItemId, 2L, "Name", "Description", 10.0, "image");

        // Mock repository method calls
        Mockito.when(menuItemRepository.findById(menuItemId)).thenReturn(Optional.of(menuItem));

        // Perform the method under test
        MenuItemEntity result = menuItemService.getMenuItemById(menuItemId);

        // Verify the expected MenuItemEntity properties
        Assertions.assertEquals(menuItem, result);
    }
}
