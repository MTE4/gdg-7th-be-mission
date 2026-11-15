package gdg.hongik.mission.service;

import gdg.hongik.mission.dto.request.ProductCreateRequest;
import gdg.hongik.mission.dto.request.ProductDeleteRequest;
import gdg.hongik.mission.dto.request.PurchaseItemRequest;
import gdg.hongik.mission.dto.request.StockUpdateRequest;
import gdg.hongik.mission.dto.response.ProductResponse;
import gdg.hongik.mission.dto.response.PurchaseResponse;
import gdg.hongik.mission.dto.response.PurchasedProductResponse;
import gdg.hongik.mission.entity.Product;
import gdg.hongik.mission.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본은 읽기 전용, 데이터를 바꾸는 메서드에만 @Transactional을 따로 붙임
public class ProductService {

    private final ProductRepository productRepository;

    // ===================== 에러 관련 (API.md "공통 규칙") =====================
    // 새 파일을 만들지 않고 이 파일 안에 함께 둡니다.

    public enum ErrorCode {
        INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 잘못됐어요."),
        PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 상품이에요."),
        DUPLICATE_PRODUCT_NAME(HttpStatus.CONFLICT, "같은 이름의 상품이 이미 있어요."),
        INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고가 모자라요.");

        private final HttpStatus status;
        private final String message;

        ErrorCode(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    // 서비스에서 던지는 예외. RuntimeException이라서 던지면 @Transactional이 롤백함
    public static class ProductException extends RuntimeException {
        private final ErrorCode errorCode;

        public ProductException(ErrorCode errorCode) {
            super(errorCode.getMessage());
            this.errorCode = errorCode;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    // 에러 응답: { "code": "PRODUCT_NOT_FOUND", "message": "없는 상품이에요." }
    public record ErrorResponse(String code, String message) {
        public static ErrorResponse from(ErrorCode errorCode) {
            return new ErrorResponse(errorCode.name(), errorCode.getMessage());
        }
    }

    // ===================== 소비자 =====================

    // 1.1 전체 상품 목록 조회
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    // 1.2 상품명 단건 조회
    public ProductResponse getProduct(String itemName) {
        return ProductResponse.from(findByItemName(itemName));
    }

    // 1.3 장바구니 일괄 구매 (하나라도 실패하면 전부 롤백)
    @Transactional
    public PurchaseResponse purchase(List<PurchaseItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ProductException(ErrorCode.INVALID_REQUEST);
        }

        // 1단계: 요청 값 검사 + 상품 조회 + 재고 확인 (아직 아무것도 바꾸지 않음)
        Set<Long> seenIds = new HashSet<>();
        Map<Long, Product> products = new HashMap<>();
        for (PurchaseItemRequest request : requests) {
            if (request == null || request.id() == null
                    || request.count() == null || request.count() < 1
                    || !seenIds.add(request.id())) {
                throw new ProductException(ErrorCode.INVALID_REQUEST);
            }

            Product product = productRepository.findById(request.id())
                    .orElseThrow(() -> new ProductException(ErrorCode.PRODUCT_NOT_FOUND));
            if (product.getQuantity() < request.count()) {
                throw new ProductException(ErrorCode.INSUFFICIENT_STOCK);
            }
            products.put(request.id(), product);
        }

        // 2단계: 재고 차감 (더티 체킹으로 커밋 시 UPDATE) + 응답 만들기
        long totalPrice = 0;
        List<PurchasedProductResponse> items = new ArrayList<>();
        for (PurchaseItemRequest request : requests) {
            Product product = products.get(request.id());
            product.decreaseQuantity(request.count());

            long price = product.getPrice() * request.count();
            totalPrice += price;
            items.add(new PurchasedProductResponse(product.getItemName(), request.count(), price));
        }

        return new PurchaseResponse(totalPrice, items);
    }

    // ===================== 관리자 =====================

    // 2.1 상품 등록
    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        if (request == null
                || request.itemName() == null || request.itemName().isBlank()
                || request.price() == null || request.price() < 0
                || request.quantity() == null || request.quantity() < 0
                || request.category() == null) {
            throw new ProductException(ErrorCode.INVALID_REQUEST);
        }
        if (productRepository.existsByItemName(request.itemName())) {
            throw new ProductException(ErrorCode.DUPLICATE_PRODUCT_NAME);
        }

        Product product = productRepository.save(new Product(
                request.itemName(), request.price(), request.quantity(), request.category()));
        return ProductResponse.from(product);
    }

    // 2.2 재고 추가
    @Transactional
    public ProductResponse addStock(StockUpdateRequest request) {
        if (request == null
                || request.itemName() == null || request.itemName().isBlank()
                || request.quantity() == null || request.quantity() < 1) {
            throw new ProductException(ErrorCode.INVALID_REQUEST);
        }

        Product product = findByItemName(request.itemName());
        product.addQuantity(request.quantity()); // 더티 체킹으로 자동 UPDATE
        return ProductResponse.from(product);
    }

    // 2.3 상품 삭제 (상품명 기준, 삭제 후 남은 전체 목록 반환)
    @Transactional
    public List<ProductResponse> deleteProduct(ProductDeleteRequest request) {
        if (request == null || request.itemName() == null || request.itemName().isBlank()) {
            throw new ProductException(ErrorCode.INVALID_REQUEST);
        }

        Product product = findByItemName(request.itemName());
        productRepository.delete(product);
        productRepository.flush(); // 삭제를 DB에 반영한 뒤 남은 목록을 조회

        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    // ===================== 내부 공통 =====================

    private Product findByItemName(String itemName) {
        return productRepository.findByItemName(itemName)
                .orElseThrow(() -> new ProductException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
