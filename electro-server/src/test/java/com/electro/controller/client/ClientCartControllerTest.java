package com.electro.controller.client;

import com.electro.dto.client.*;
import com.electro.entity.cart.Cart;
import com.electro.entity.cart.CartVariant;
import com.electro.entity.cart.CartVariantKey;
import com.electro.exception.ResourceNotFoundException;
import com.electro.mapper.client.ClientCartMapper;
import com.electro.repository.cart.CartRepository;
import com.electro.repository.cart.CartVariantRepository;
import com.electro.repository.inventory.DocketVariantRepository;
import com.electro.utils.InventoryUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClientCartControllerTest {

    @InjectMocks
    private ClientCartController clientCartController;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private DocketVariantRepository docketVariantRepository;

    @Mock
    private ClientCartMapper clientCartMapper;

    @Mock
    private Authentication  authentication;

    @Mock
    private CartVariantRepository cartVariantRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * Test khi có giỏ hàng → trả về Optional<Cart> hợp lệ.
     */
    @Test
    void testGetCart_WhenCartExists_ReturnsCartAsObjectNode() {
        when(authentication.getName()).thenReturn("user1");

        Cart mockCart = new Cart();
        ClientCartResponse mockResponse = new ClientCartResponse();
        mockResponse.setCartId(123L);

        when(cartRepository.findByUsername("user1")).thenReturn(Optional.of(mockCart));
        when(clientCartMapper.entityToResponse(mockCart)).thenReturn(mockResponse);

        ResponseEntity<ObjectNode> responseEntity = clientCartController.getCart(authentication);

        assertEquals(200, responseEntity.getStatusCodeValue());
        ObjectNode body = responseEntity.getBody();
        assertNotNull(body);
        assertEquals(123L, body.get("cartId").asLong());
    }

    /**
     * Test khi không có giỏ hàng → trả về Optional.empty() → trả về ObjectNode rỗng.
     */
    @Test
    void testGetCart_WhenCartDoesNotExist_ReturnsEmptyObjectNode() {
        when(authentication.getName()).thenReturn("user2");

        when(cartRepository.findByUsername("user2")).thenReturn(Optional.empty());

        ResponseEntity<ObjectNode> responseEntity = clientCartController.getCart(authentication);

        assertEquals(200, responseEntity.getStatusCodeValue());
        ObjectNode body = responseEntity.getBody();
        assertNotNull(body);
        assertTrue(body.isEmpty(), "ObjectNode phải rỗng khi không có cart");
    }


    /**
     * Test khi cartId là null → tạo mới cart.
     */
    @Test
    void testSaveCart_WhenCartIdIsNull_ShouldCreateNewCart() {
        // Arrange
        ClientCartRequest request = new ClientCartRequest();
        request.setCartId(null);
        request.setUserId(1L);
        request.setStatus(1);
        request.setUpdateQuantityType(UpdateQuantityType.INCREMENTAL);

        Cart cartBeforeSave = new Cart();
        Cart savedCart = new Cart();
        ClientCartResponse response = new ClientCartResponse();

        when(clientCartMapper.requestToEntity(request)).thenReturn(cartBeforeSave);
        when(docketVariantRepository.findByVariantId(anyLong())).thenReturn(Collections.emptyList());
        when(cartRepository.save(cartBeforeSave)).thenReturn(savedCart);
        when(clientCartMapper.entityToResponse(savedCart)).thenReturn(response);

        // Act
        ResponseEntity<ClientCartResponse> result = clientCartController.saveCart(request);

        // Assert
        assertEquals(200, result.getStatusCodeValue());
        assertEquals(response, result.getBody());
        verify(cartRepository).save(cartBeforeSave);
    }

    /**
     * Test khi cartId không null và tìm thấy cart trong DB → cập nhật cart.
     */
    @Test
    void testSaveCart_WhenCartIdExists_ShouldUpdateCart() {
        // Arrange
        ClientCartRequest request = new ClientCartRequest();
        request.setCartId(100L);

        Cart existingCart = new Cart();
        Cart updatedCart = new Cart();
        ClientCartResponse response = new ClientCartResponse();

        when(cartRepository.findById(100L)).thenReturn(Optional.of(existingCart));
        when(clientCartMapper.partialUpdate(existingCart, request)).thenReturn(updatedCart);
        when(docketVariantRepository.findByVariantId(anyLong())).thenReturn(Collections.emptyList());
        when(cartRepository.save(updatedCart)).thenReturn(updatedCart);
        when(clientCartMapper.entityToResponse(updatedCart)).thenReturn(response);

        // Act
        ResponseEntity<ClientCartResponse> result = clientCartController.saveCart(request);

        // Assert
        assertEquals(200, result.getStatusCodeValue());
        assertEquals(response, result.getBody());
    }

    /**
     * Test khi cartId không null nhưng không tìm thấy trong DB → throw ResourceNotFoundException.
     */
    @Test
    void testSaveCart_WhenCartIdNotFound_ShouldThrowException() {
        // Arrange
        ClientCartRequest request = new ClientCartRequest();
        request.setCartId(999L);

        when(cartRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> {
            clientCartController.saveCart(request);
        });
        assertEquals("Cart not found with id: '999'", ex.getMessage());

    }

    /**
     * Test khi số lượng vượt quá tồn kho → throw RuntimeException.
     */
    @Test
    void testSaveCart_WhenQuantityExceedsInventory_ShouldThrowException() {
        // Arrange
        ClientCartRequest request = new ClientCartRequest();
        request.setCartId(null);
        request.setUserId(1L);

        Cart cart = new Cart();
        CartVariant cartVariant = new CartVariant();
        CartVariantKey key = new CartVariantKey();
        key.setVariantId(10L);
        cartVariant.setCartVariantKey(key);
        cartVariant.setQuantity(100);

        cart.setCartVariants(Set.of(cartVariant));
        request.setCartItems(Set.of(new ClientCartVariantRequest()));

        when(clientCartMapper.requestToEntity(request)).thenReturn(cart);
        when(docketVariantRepository.findByVariantId(10L)).thenReturn(Collections.emptyList());
        // Giả sử tồn kho trả về là 50
        Map<String, Integer> fakeInventory = new HashMap<>();
        fakeInventory.put("canBeSold", 50);
        mockStatic(InventoryUtils.class).when(() -> InventoryUtils.calculateInventoryIndices(any()))
                .thenReturn(fakeInventory);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            clientCartController.saveCart(request);
        });

        assertEquals("Variant quantity cannot greater than variant inventory", exception.getMessage());
    }


    /**
     * Test cập nhật số lượng hợp lệ sản phẩm đã có trong giỏ hàng → thành công.
     */
    @Test
    void testSaveCart_UpdateQuantityOfExistingItem_ShouldUpdateSuccessfully() {
        // Arrange
        ClientCartRequest request = new ClientCartRequest();
        request.setCartId(1L);
        request.setUpdateQuantityType(UpdateQuantityType.OVERRIDE);
        request.setStatus(1);

        ClientCartVariantRequest variantRequest = new ClientCartVariantRequest();
        variantRequest.setVariantId(100L);
        variantRequest.setQuantity(2); // tăng số lượng

        request.setCartItems(Set.of(variantRequest));

        // Mock existing cart
        CartVariant existingVariant = new CartVariant();
        CartVariantKey key = new CartVariantKey(1L, 100L);
        existingVariant.setCartVariantKey(key);
        existingVariant.setQuantity(1);

        Cart existingCart = new Cart();
        existingCart.setCartVariants(Set.of(existingVariant));

        Cart updatedCart = new Cart();
        updatedCart.setCartVariants(Set.of(existingVariant)); // đã update

        ClientCartResponse response = new ClientCartResponse();
        response.setCartId(1L);

        when(cartRepository.findById(1L)).thenReturn(Optional.of(existingCart));
        when(clientCartMapper.partialUpdate(existingCart, request)).thenReturn(updatedCart);
        when(docketVariantRepository.findByVariantId(100L)).thenReturn(Collections.emptyList());
        // Giả sử tồn kho trả về là 50
        Map<String, Integer> fakeInventory = new HashMap<>();
        fakeInventory.put("canBeSold", 50);
        mockStatic(InventoryUtils.class).when(() -> InventoryUtils.calculateInventoryIndices(any()))
                .thenReturn(fakeInventory);
        when(cartRepository.save(updatedCart)).thenReturn(updatedCart);
        when(clientCartMapper.entityToResponse(updatedCart)).thenReturn(response);

        // Act
        ResponseEntity<ClientCartResponse> result = clientCartController.saveCart(request);

        // Assert
        assertEquals(200, result.getStatusCodeValue());
        assertEquals(1L, result.getBody().getCartId());
    }

    /**
     * Test cập nhật số lượng vượt tồn kho sản phẩm đã có trong giỏ hàng → throw RuntimeException.
     */
    @Test
    void testSaveCart_WhenQuantityExceedsInventory_ShouldThrowRuntimeException() {
        // Arrange
        ClientCartRequest request = new ClientCartRequest();
        request.setCartId(1L);
        request.setUpdateQuantityType(UpdateQuantityType.OVERRIDE);
        request.setStatus(1);

        ClientCartVariantRequest variantRequest = new ClientCartVariantRequest();
        variantRequest.setVariantId(100L);
        variantRequest.setQuantity(10); // vượt quá tồn kho
        request.setCartItems(Set.of(variantRequest));

        // Mock existing cart
        CartVariant existingVariant = new CartVariant();
        CartVariantKey key = new CartVariantKey(1L, 100L);
        existingVariant.setCartVariantKey(key);
        existingVariant.setQuantity(5);

        Cart existingCart = new Cart();
        existingCart.setCartVariants(Set.of(existingVariant));

        // Mock updated cart với quantity = 10 (vượt kho)
        CartVariant updatedVariant = new CartVariant();
        updatedVariant.setCartVariantKey(key);
        updatedVariant.setQuantity(10);

        Cart updatedCart = new Cart();
        updatedCart.setCartVariants(Set.of(updatedVariant));

        when(cartRepository.findById(1L)).thenReturn(Optional.of(existingCart));
        when(clientCartMapper.partialUpdate(existingCart, request)).thenReturn(updatedCart);
        when(docketVariantRepository.findByVariantId(100L)).thenReturn(Collections.emptyList());

        Map<String, Integer> fakeInventory = new HashMap<>();
        fakeInventory.put("canBeSold", 5);
        mockStatic(InventoryUtils.class).when(() -> InventoryUtils.calculateInventoryIndices(any()))
                .thenReturn(fakeInventory);
//        assertThrows(RuntimeException.class, () -> clientCartController.saveCart(request));
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            clientCartController.saveCart(request);
        });

        assertEquals("Variant quantity cannot greater than variant inventory", exception.getMessage());
    }

    /**
     * Test xoá truyền vào danh sách hợp lệ → gọi được deleteAllById(...) → trả về status 204.
     */
    @Test
    void testDeleteCartItems_WithValidList_ShouldDeleteAndReturnNoContent() {
        // Tình huống: có danh sách item hợp lệ cần xóa
        ClientCartVariantKeyRequest item1 = new ClientCartVariantKeyRequest();
        item1.setCartId(1L);
        item1.setVariantId(101L);

        ClientCartVariantKeyRequest item2 = new ClientCartVariantKeyRequest();
        item2.setCartId(1L);
        item2.setVariantId(102L);

        List<ClientCartVariantKeyRequest> requestList = Arrays.asList(item1, item2);

        // Gọi phương thức controller
        ResponseEntity<Void> response = clientCartController.deleteCartItems(requestList);

        // Capture đối số gọi vào deleteAllById
        ArgumentCaptor<List<CartVariantKey>> captor = ArgumentCaptor.forClass(List.class);
        verify(cartVariantRepository, times(1)).deleteAllById(captor.capture());

        List<CartVariantKey> capturedKeys = captor.getValue();
        assertEquals(2, capturedKeys.size());

        boolean hasItem1 = capturedKeys.stream().anyMatch(k ->
                k.getCartId().equals(1L) && k.getVariantId().equals(101L));
        boolean hasItem2 = capturedKeys.stream().anyMatch(k ->
                k.getCartId().equals(1L) && k.getVariantId().equals(102L));

        assertTrue(hasItem1);
        assertTrue(hasItem2);

        assertEquals(204, response.getStatusCodeValue());
    }

    /**
     * Test xoá truyền vào danh sách rỗng → không có gì để xóa.
     */
    @Test
    void testDeleteCartItems_WithEmptyList_ShouldStillReturnNoContent() {
        List<ClientCartVariantKeyRequest> requestList = Collections.emptyList();

        ResponseEntity<Void> response = clientCartController.deleteCartItems(requestList);

        ArgumentCaptor<List<CartVariantKey>> captor = ArgumentCaptor.forClass(List.class);
        verify(cartVariantRepository).deleteAllById(captor.capture());

        List<CartVariantKey> capturedKeys = captor.getValue();
        assertTrue(capturedKeys.isEmpty());

        assertEquals(204, response.getStatusCodeValue());
    }


}
