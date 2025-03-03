
package com.example.seckill.exception;

import com.example.seckill.vo.Result;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(GlobalException.class)
    public Result<String> handleGlobalException(GlobalException e) {
        return Result.error(e.getCode(), e.getMessage());
    }
    
    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException e) {
        List<ObjectError> errors = e.getAllErrors();
        ObjectError error = errors.get(0);
        return Result.error(500, error.getDefaultMessage());
    }
    
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        e.printStackTrace();
        return Result.error(500, "服务端异常: " + e.getMessage());
    }
}