package com.example.knowledge.web.dto;

public record ApiResponse<T>(Integer code, String message, T data) {

    private static final Integer SUCCESS_CODE = 0;
    private static final String SUCCESS_MESSAGE = "success";

    /**
     * 创建成功响应。
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 创建失败响应。
     */
    public static ApiResponse<Void> failure(Integer code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
