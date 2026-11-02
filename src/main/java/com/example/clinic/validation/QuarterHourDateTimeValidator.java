package com.example.clinic.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDateTime;

/**
 * Checks full-quarter-hour alignment for a local date-time constraint.
 *
 * <p>Selected by the validated value's type. Null is accepted; otherwise minute must be divisible by 15
 * and second/nanosecond must be zero. It does not validate the zone, past/future or visit collisions.
 */
public class QuarterHourDateTimeValidator implements ConstraintValidator<QuarterHour, LocalDateTime> {

    /**
     * Accepts null or a date-time at an exact quarter-hour; other constraint rules are separate.
     *
     * @param value the local date-time, possibly null
     * @param context the validation context, unused
     * @return whether alignment is valid
     */
    @Override
    public boolean isValid(LocalDateTime value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.getMinute() % 15 == 0 && value.getSecond() == 0 && value.getNano() == 0;
    }
}
