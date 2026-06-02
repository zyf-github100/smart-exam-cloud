package com.smart.exam.common.web.handler;

import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.core.model.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBizExceptionCodeToHttpStatus() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBizException(
                new BizException(ErrorCode.FORBIDDEN, "Access denied")
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ErrorCode.FORBIDDEN.getCode(), response.getBody().getCode());
    }
}
