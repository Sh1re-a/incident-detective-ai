package dev.shirwac.incidentdetective.domain.diagnosis;

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
@Constraint(validatedBy = DiagnosisValidator.class)
public @interface ValidDiagnosis {

    String message() default "diagnosis fields do not match its status";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
