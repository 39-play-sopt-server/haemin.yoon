package org.sopt.global.exception;

import java.util.Comparator;
import java.util.List;
import org.sopt.global.response.ValidationViolation;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/** 검증 엔진의 메시지·거부된 값·객체명을 공개하지 않고 안정적인 API 오류로 변환합니다. */
final class RequestValidationErrors {
  private RequestValidationErrors() {}

  static List<ValidationViolation> from(BindingResult bindingResult) {
    return bindingResult.getAllErrors().stream()
        .map(RequestValidationErrors::toViolation)
        .distinct()
        .sorted(
            Comparator.comparing(
                    ValidationViolation::field, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ValidationViolation::code)
                .thenComparing(ValidationViolation::message))
        .toList();
  }

  private static ValidationViolation toViolation(ObjectError error) {
    String field = error instanceof FieldError fieldError ? fieldError.getField() : null;
    // 메시지나 Enum 스캔으로 오류 코드를 역조회하지 않습니다.
    String constraint = error.getCode();
    if ("NotBlank".equals(constraint) || "NotNull".equals(constraint)) {
      return new ValidationViolation(field, "REQUIRED", "필수 입력값입니다.");
    }
    // 미등록 제약의 메시지에도 내부 정보나 입력값이 들어갈 수 있으므로 안전한 문구를 사용합니다.
    return new ValidationViolation(field, "INVALID_VALUE", "입력값이 올바르지 않습니다.");
  }
}
