package com.template.api.ordering.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderingError Unit Tests")
class OrderingErrorTest {

    @ParameterizedTest
    @EnumSource(OrderingError.class)
    @DisplayName("OrderingError codes should match enum constant names")
    void should_match_enum_constant_name_when_error_code_inspected(OrderingError error) {
        assertThat(error.code()).isEqualTo(error.name());
    }

    @ParameterizedTest
    @EnumSource(OrderingError.class)
    @DisplayName("OrderingError codes should adhere to ORDERING_[ENTIDAD]_[MOTIVO] pattern")
    void should_follow_standard_naming_pattern_when_error_code_inspected(OrderingError error) {
        assertThat(error.code())
                .startsWith("ORDERING_")
                .matches("^[A-Z]+_[A-Z]+_[A-Z_]+$");
    }
}
