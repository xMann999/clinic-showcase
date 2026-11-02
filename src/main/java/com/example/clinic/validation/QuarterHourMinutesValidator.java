package com.example.clinic.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Checks divisibility by 15 for an integer minute-duration constraint.
 *
 * <p>Selected by the validated value's type. Null, zero and negative multiples of 15 pass this constraint;
 * NotNull and duration bounds are enforced separately. This validator performs no state-dependent checks.
 */
public class QuarterHourMinutesValidator implements ConstraintValidator<QuarterHour, Integer> {

    /**
     * Accepts null or an integer divisible by 15, without enforcing positivity or duration bounds.
     *
     * @param value the duration, possibly null
     * @param context the validation context, unused
     * @return whether divisibility is valid
     */
    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || value % 15 == 0;
    }
}
