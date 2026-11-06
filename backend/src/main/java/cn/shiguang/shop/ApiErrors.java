// AI-assisted implementation with Codex.
package cn.shiguang.shop;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请检查输入内容：用户名3至32位字母数字或下划线，密码至少8位，邮箱和必填项须有效。");
    }
}
