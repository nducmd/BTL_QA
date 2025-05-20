package com.electro.service;

import com.electro.config.payment.paypal.PayPalHttpClient;
import com.electro.dto.client.ClientConfirmedOrderResponse;
import com.electro.dto.client.ClientSimpleOrderRequest;
import com.electro.dto.payment.OrderStatus;
import com.electro.dto.payment.PaypalResponse;
import com.electro.entity.address.Address;
import com.electro.entity.address.District;
import com.electro.entity.address.Province;
import com.electro.entity.address.Ward;
import com.electro.entity.authentication.User;
import com.electro.entity.cart.Cart;
import com.electro.entity.cart.CartVariant;
import com.electro.entity.cashbook.PaymentMethodType;
import com.electro.entity.product.Product;
import com.electro.entity.product.Variant;
import com.electro.entity.promotion.Promotion;
import com.electro.exception.ResourceNotFoundException;
import com.electro.repository.authentication.UserRepository;
import com.electro.repository.cart.CartRepository;
import com.electro.repository.order.OrderRepository;
import com.electro.repository.promotion.PromotionRepository;
import com.electro.service.order.OrderServiceImpl;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceImplTest {

    @InjectMocks
    private OrderServiceImpl orderService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PayPalHttpClient payPalHttpClient;

    @BeforeEach
    void setup() {
        // Giả lập thông tin đăng nhập
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User mockUser() {
        Address address = new Address()
                .setLine("123 Street")
                .setWard(new Ward().setName("Ward A"))
                .setDistrict(new District().setName("District B"))
                .setProvince(new Province().setName("Province C"));
        return new User()
                .setUsername("testuser")
                .setFullname("Test User")
                .setPhone("0123456789")
                .setAddress(address);
    }

    private Variant mockVariant(BigDecimal price) {
        Product product = new Product();
        product.setId(1L);
        Variant variant = new Variant();
        variant.setId(2L);
        variant.setPrice(price.doubleValue());
        variant.setProduct(product);
        return variant;
    }

    private Cart mockCart(Variant variant, int quantity) {
        Cart cart = new Cart().setStatus(1);
        CartVariant cartVariant = new CartVariant()
                .setVariant(variant)
                .setQuantity(quantity)
                .setCart(cart);
        cart.setCartVariants(Set.of(cartVariant));
        return cart;
    }

    @Test
    void createClientOrder_cashNoPromotion_shouldReturnValidResponse() {
        // Arrange
        User user = mockUser();
        Variant variant = mockVariant(BigDecimal.valueOf(100000));
        Cart cart = mockCart(variant, 2);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(cartRepository.findByUsername("testuser")).thenReturn(Optional.of(cart));
        when(promotionRepository.findActivePromotionByProductId(1L)).thenReturn(List.of());

        ClientSimpleOrderRequest request = new ClientSimpleOrderRequest();
        request.setPaymentMethodType(PaymentMethodType.CASH);

        // Act
        ClientConfirmedOrderResponse response = orderService.createClientOrder(request);

        // Assert
        assertEquals(PaymentMethodType.CASH, response.getOrderPaymentMethodType());
        assertNotNull(response.getOrderCode());
        assertNull(response.getOrderPaypalCheckoutLink());

        verify(orderRepository).save(any());
        verify(cartRepository).save(cart);
    }

    @Test
    void createClientOrder_cashWithPromotion_shouldCalculateDiscountedTotal() {
        // Arrange
        User user = mockUser();
        Variant variant = mockVariant(BigDecimal.valueOf(200000));
        Cart cart = mockCart(variant, 1);
        Promotion promotion = new Promotion().setPercent(10);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(cartRepository.findByUsername("testuser")).thenReturn(Optional.of(cart));
        when(promotionRepository.findActivePromotionByProductId(1L)).thenReturn(List.of(promotion));

        ClientSimpleOrderRequest request = new ClientSimpleOrderRequest();
        request.setPaymentMethodType(PaymentMethodType.CASH);

        // Act
        ClientConfirmedOrderResponse response = orderService.createClientOrder(request);

        // Assert: price = 200,000 - 10% = 180,000
        verify(orderRepository).save(argThat(order -> {
            return order.getTotalAmount().compareTo(BigDecimal.valueOf(180000)) == 0;
        }));
    }

    @Test
    void createClientOrder_paypalSuccess_shouldReturnCheckoutLink() throws Exception {
        // Arrange
        User user = mockUser();
        Variant variant = mockVariant(BigDecimal.valueOf(100000));
        Cart cart = mockCart(variant, 1);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(cartRepository.findByUsername("testuser")).thenReturn(Optional.of(cart));
        when(promotionRepository.findActivePromotionByProductId(1L)).thenReturn(List.of());

        PaypalResponse paypalResponse = new PaypalResponse();
        paypalResponse.setId("paypal-id");
        paypalResponse.setStatus(OrderStatus.CREATED);

        PaypalResponse.Link link = new PaypalResponse.Link();
        link.setHref("https://paypal.com/checkout");
        link.setRel("approve");
        link.setMethod("GET");

        paypalResponse.setLinks(List.of(link));

        when(payPalHttpClient.createPaypalTransaction(any())).thenReturn(paypalResponse);

        ClientSimpleOrderRequest request = new ClientSimpleOrderRequest();
        request.setPaymentMethodType(PaymentMethodType.PAYPAL);

        // Act
        ClientConfirmedOrderResponse response = orderService.createClientOrder(request);

        // Assert
        assertEquals("https://paypal.com/checkout", response.getOrderPaypalCheckoutLink());
        verify(orderRepository).save(any());
    }

    @SneakyThrows
    @Test
    void createClientOrder_paypalFail_shouldThrowException() {
        // Arrange
        User user = mockUser();
        Variant variant = mockVariant(BigDecimal.valueOf(100000));
        Cart cart = mockCart(variant, 1);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(cartRepository.findByUsername("testuser")).thenReturn(Optional.of(cart));
        when(promotionRepository.findActivePromotionByProductId(1L)).thenReturn(List.of());

        when(payPalHttpClient.createPaypalTransaction(any())).thenThrow(new RuntimeException("PayPal error"));

        ClientSimpleOrderRequest request = new ClientSimpleOrderRequest();
        request.setPaymentMethodType(PaymentMethodType.PAYPAL);

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createClientOrder(request));
        assertTrue(ex.getMessage().contains("Cannot create PayPal transaction request"));
    }

    @Test
    void createClientOrder_invalidMethod_shouldThrowException() {
        // Arrange
        User user = mockUser();
        Variant variant = mockVariant(BigDecimal.valueOf(100000));
        Cart cart = mockCart(variant, 1);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(cartRepository.findByUsername("testuser")).thenReturn(Optional.of(cart));

        ClientSimpleOrderRequest request = new ClientSimpleOrderRequest();
        request.setPaymentMethodType(null); // Unknown

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.createClientOrder(request));
        assertEquals("Cannot identify payment method", ex.getMessage());
    }

    @Test
    void createClientOrder_userNotFound_shouldThrow() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        ClientSimpleOrderRequest request = new ClientSimpleOrderRequest();
        request.setPaymentMethodType(PaymentMethodType.CASH);

        assertThrows(UsernameNotFoundException.class, () -> orderService.createClientOrder(request));
    }

    @Test
    void createClientOrder_cartNotFound_shouldThrow() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser()));
        when(cartRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        ClientSimpleOrderRequest request = new ClientSimpleOrderRequest();
        request.setPaymentMethodType(PaymentMethodType.CASH);

        assertThrows(ResourceNotFoundException.class, () -> orderService.createClientOrder(request));
    }
}
