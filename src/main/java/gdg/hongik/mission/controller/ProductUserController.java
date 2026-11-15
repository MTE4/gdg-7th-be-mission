package gdg.hongik.mission.controller;

import gdg.hongik.mission.dto.request.PurchaseItemRequest;
import gdg.hongik.mission.dto.response.ProductResponse;
import gdg.hongik.mission.dto.response.PurchaseResponse;
import gdg.hongik.mission.service.ProductService;
import gdg.hongik.mission.service.ProductService.ErrorCode;
import gdg.hongik.mission.service.ProductService.ErrorResponse;
import gdg.hongik.mission.service.ProductService.ProductException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductUserController {

    private final ProductService productService;

    // 1.1 전체 상품 목록 조회: GET /products
    @GetMapping
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }

    // 1.2 상품명 단건 조회: GET /products?itemName=원피스
    @GetMapping(params = "itemName")
    public ProductResponse getProduct(@RequestParam String itemName) {
        return productService.getProduct(itemName);
    }

    // 1.3 장바구니 일괄 구매: POST /products/purchase
    @PostMapping("/purchase")
    public PurchaseResponse purchase(@RequestBody List<PurchaseItemRequest> requests) {
        return productService.purchase(requests);
    }

    // ===================== 에러 응답 =====================

    @ExceptionHandler(ProductException.class)
    public ResponseEntity<ErrorResponse> handleProductException(ProductException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    // JSON 형식 오류, 숫자 자리에 문자열, 필수 파라미터 누락 등
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }
}
