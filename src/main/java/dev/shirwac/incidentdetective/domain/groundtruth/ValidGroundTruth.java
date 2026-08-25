package dev.shirwac.incidentdetective.domain.groundtruth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GroundTruthValidator.class)
public @interface ValidGroundTruth {

    String message() default "ground truth fields do not match its expected status";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
