package com.laioffer.onlineorder;

import com.laioffer.onlineorder.entity.MenuItemEntity;
import com.laioffer.onlineorder.entity.RestaurantEntity;
import com.laioffer.onlineorder.model.RestaurantDto;
import com.laioffer.onlineorder.repository.MenuItemRepository;
import com.laioffer.onlineorder.repository.RestaurantRepository;
import com.laioffer.onlineorder.service.RestaurantService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class RestaurantServiceTests {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    private RestaurantService restaurantService;

    @BeforeEach
    void setup() {
        restaurantService = new RestaurantService(restaurantRepository, menuItemRepository);
    }

    @Test
    void getRestaurants_shouldReturnRestaurantsWithMenuItems() {
        // Mock data
        List<RestaurantEntity> restaurants = List.of(
                new RestaurantEntity(1L, "Restaurant1", "Address1", "Phone1", "image1"),
                new RestaurantEntity(2L, "Restaurant2", "Address2", "Phone2", "image2")
        );
        List<MenuItemEntity> menuItems = List.of(
                new MenuItemEntity(1L, 1L, "Name1", "Description1", 10.0, "image1"),
                new MenuItemEntity(2L, 1L, "Name2", "Description2", 20.0, "image2"),
                new MenuItemEntity(3L, 2L, "Name3", "Description3", 30.0, "image3")
        );

        // Mock repository method calls
        Mockito.when(restaurantRepository.findAll()).thenReturn(restaurants);
        Mockito.when(menuItemRepository.findAll()).thenReturn(menuItems);

        // Perform the method under test
        List<RestaurantDto> results = restaurantService.getRestaurants();

        // Verify the expected RestaurantDto properties
        Assertions.assertEquals(restaurants.size(), results.size());

        RestaurantDto firstRestaurant = results.get(0);
        Assertions.assertEquals(restaurants.get(0).id(), firstRestaurant.id());
        Assertions.assertEquals(restaurants.get(0).name(), firstRestaurant.name());
        Assertions.assertEquals(2, firstRestaurant.menuItems().size());
        Assertions.assertEquals(menuItems.get(0).id(), firstRestaurant.menuItems().get(0).id());
        Assertions.assertEquals(menuItems.get(1).id(), firstRestaurant.menuItems().get(1).id());

        RestaurantDto secondRestaurant = results.get(1);
        Assertions.assertEquals(restaurants.get(1).id(), secondRestaurant.id());
        Assertions.assertEquals(restaurants.get(1).name(), secondRestaurant.name());
        Assertions.assertEquals(1, secondRestaurant.menuItems().size());
        Assertions.assertEquals(menuItems.get(2).id(), secondRestaurant.menuItems().get(0).id());
    }

    @Test
    void getRestaurants_whenRestaurantHasNoMenuItems_shouldReturnRestaurantWithNullMenuItems() {
        // Mock data
        List<RestaurantEntity> restaurants = List.of(
                new RestaurantEntity(1L, "Restaurant1", "Address1", "Phone1", "image1")
        );
        List<MenuItemEntity> menuItems = List.of(
                new MenuItemEntity(1L, 2L, "Name1", "Description1", 10.0, "image1")
        );

        // Mock repository method calls
        Mockito.when(restaurantRepository.findAll()).thenReturn(restaurants);
        Mockito.when(menuItemRepository.findAll()).thenReturn(menuItems);

        // Perform the method under test
        List<RestaurantDto> results = restaurantService.getRestaurants();

        // Verify the expected RestaurantDto properties
        Assertions.assertEquals(1, results.size());
        Assertions.assertEquals(restaurants.get(0).id(), results.get(0).id());
        Assertions.assertNull(results.get(0).menuItems());
    }
}
