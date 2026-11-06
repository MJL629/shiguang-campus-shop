// AI-assisted implementation with Codex.
package cn.shiguang.shop;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalid(MethodArgumentNotValidException error) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请检查必填项、输入格式和数值范围。");
  }
}
