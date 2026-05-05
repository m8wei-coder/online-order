package com.laioffer.onlineorder;

import com.laioffer.onlineorder.entity.CartEntity;
import com.laioffer.onlineorder.entity.CustomerEntity;
import com.laioffer.onlineorder.repository.CartRepository;
import com.laioffer.onlineorder.repository.CustomerRepository;
import com.laioffer.onlineorder.service.CustomerService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;

@ExtendWith(MockitoExtension.class)
public class CustomerServiceTests {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserDetailsManager userDetailsManager;

    private CustomerService customerService;

    @BeforeEach
    void setup() {
        customerService = new CustomerService(
                cartRepository,
                customerRepository,
                passwordEncoder,
                userDetailsManager);
    }

    @Test
    void signUp_shouldCreateUserUpdateCustomerNameAndCreateCart() {
        // Mock data
        String email = "TEST@EXAMPLE.COM";
        String normalizedEmail = "test@example.com";
        String password = "password";
        String encodedPassword = "encoded-password";
        String firstName = "First";
        String lastName = "Last";
        CustomerEntity savedCustomer = new CustomerEntity(1L, normalizedEmail, encodedPassword, true, firstName, lastName);

        // Mock repository method calls
        Mockito.when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        Mockito.when(customerRepository.findByEmail(normalizedEmail)).thenReturn(savedCustomer);

        // Perform the method under test
        customerService.signUp(email, password, firstName, lastName);

        // Verify the repository method calls
        ArgumentCaptor<UserDetails> userCaptor = ArgumentCaptor.forClass(UserDetails.class);
        Mockito.verify(userDetailsManager).createUser(userCaptor.capture());

        UserDetails createdUser = userCaptor.getValue();
        Assertions.assertEquals(normalizedEmail, createdUser.getUsername());
        Assertions.assertEquals(encodedPassword, createdUser.getPassword());
        Assertions.assertTrue(createdUser.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_USER")));

        Mockito.verify(customerRepository).updateNameByEmail(normalizedEmail, firstName, lastName);
        Mockito.verify(customerRepository).findByEmail(normalizedEmail);
        Mockito.verify(cartRepository).save(new CartEntity(null, savedCustomer.id(), 0.0));
    }

    @Test
    void getCustomerByEmail_shouldReturnCustomer() {
        // Mock data
        String email = "test@example.com";
        CustomerEntity customer = new CustomerEntity(1L, email, "password", true, "First", "Last");

        // Mock repository method calls
        Mockito.when(customerRepository.findByEmail(email)).thenReturn(customer);

        // Perform the method under test
        CustomerEntity result = customerService.getCustomerByEmail(email);

        // Verify the expected CustomerEntity properties
        Assertions.assertEquals(customer, result);
    }
}
