package gdg.hongik.mission.controller;

import gdg.hongik.mission.dto.request.ProductCreateRequest;
import gdg.hongik.mission.dto.request.ProductDeleteRequest;
import gdg.hongik.mission.dto.request.StockUpdateRequest;
import gdg.hongik.mission.dto.response.ProductResponse;
import gdg.hongik.mission.service.ProductService;
import gdg.hongik.mission.service.ProductService.ErrorCode;
import gdg.hongik.mission.service.ProductService.ErrorResponse;
import gdg.hongik.mission.service.ProductService.ProductException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductService productService;

    // 2.1 상품 등록: POST /admin/products
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@RequestBody ProductCreateRequest request) {
        return productService.createProduct(request);
    }

    // 2.2 재고 추가: PATCH /admin/products/stock
    @PatchMapping("/stock")
    public ProductResponse addStock(@RequestBody StockUpdateRequest request) {
        return productService.addStock(request);
    }

    // 2.3 상품 삭제: DELETE /admin/products
    @DeleteMapping
    public List<ProductResponse> deleteProduct(@RequestBody ProductDeleteRequest request) {
        return productService.deleteProduct(request);
    }

    // ===================== 에러 응답 =====================

    @ExceptionHandler(ProductException.class)
    public ResponseEntity<ErrorResponse> handleProductException(ProductException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    // JSON 형식 오류, 없는 카테고리 값, 숫자 자리에 문자열 등
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }
}
