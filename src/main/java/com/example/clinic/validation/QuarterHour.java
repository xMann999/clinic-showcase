package com.example.clinic.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a quarter-hour Bean Validation constraint for local starts or integer minute durations.
 *
 * <p>Local starts require minutes divisible by 15 and zero seconds/nanoseconds. Integers need only be
 * divisible by 15; positivity and maximum duration are separate constraints. Both validators accept
 * null so presence can be reported separately by NotNull. Validation runs when a caller invokes Bean
 * Validation, such as MVC's validated request binding; this annotation performs no database checks.
 */
@Documented
@Constraint(validatedBy = { QuarterHourDateTimeValidator.class, QuarterHourMinutesValidator.class })
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface QuarterHour {
    String message() default "must be aligned to a full quarter of an hour (00, 15, 30, 45)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
