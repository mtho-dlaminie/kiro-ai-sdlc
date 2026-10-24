package com.example.fintech.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Single centralized advice translating exceptions into RFC 7807 {@code application/problem+json}
 * responses. Controllers never build error bodies by hand. No stack traces or internal messages are
 * leaked to clients; details are logged at the boundary with a correlation id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI TYPE_NOT_FOUND = URI.create("urn:fintech:error:customer-not-found");
    private static final URI TYPE_INVALID_REQUEST = URI.create("urn:fintech:error:invalid-request");
    private static final URI TYPE_VALIDATION = URI.create("urn:fintech:error:validation");
    private static final URI TYPE_DEPENDENCY_UNAVAILABLE = URI.create("urn:fintech:error:dependency-unavailable");

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handleCustomerNotFound(CustomerNotFoundException ex) {
        String correlationId = newCorrelationId();
        log.warn("Customer not found [correlationId={}]", correlationId);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "The requested customer does not exist.");
        problem.setType(TYPE_NOT_FOUND);
        problem.setTitle("Customer Not Found");
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        String correlationId = newCorrelationId();
        log.warn("Invalid request [correlationId={}]", correlationId);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(TYPE_INVALID_REQUEST);
        problem.setTitle("Invalid Request");
        if (ex.field() != null) {
            problem.setProperty("field", ex.field());
        }
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> violations = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            violations.add(new FieldViolation(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        return validationProblem(violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            violations.add(new FieldViolation(lastPathNode(violation), violation.getMessage()));
        }
        return validationProblem(violations);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<FieldViolation> violations = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String field = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error ->
                    violations.add(new FieldViolation(field, error.getDefaultMessage())));
        });
        return validationProblem(violations);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String correlationId = newCorrelationId();
        log.warn("Request parameter type mismatch [correlationId={}]", correlationId);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request parameter '" + ex.getName() + "' has an invalid format.");
        problem.setType(TYPE_INVALID_REQUEST);
        problem.setTitle("Invalid Request");
        problem.setProperty("field", ex.getName());
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    public ProblemDetail handleDependencyUnavailable(DependencyUnavailableException ex) {
        String correlationId = newCorrelationId();
        log.error("Dependency unavailable [dependency={}, correlationId={}]", ex.dependency(), correlationId);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "A required dependency is currently unavailable. Please retry later.");
        problem.setType(TYPE_DEPENDENCY_UNAVAILABLE);
        problem.setTitle("Dependency Unavailable");
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    private ProblemDetail validationProblem(List<FieldViolation> violations) {
        String correlationId = newCorrelationId();
        log.warn("Request validation failed [correlationId={}, violations={}]", correlationId, violations.size());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "One or more request values are invalid.");
        problem.setType(TYPE_VALIDATION);
        problem.setTitle("Validation Failed");
        problem.setProperty("errors", violations);
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    private static String lastPathNode(ConstraintViolation<?> violation) {
        String field = null;
        for (var node : violation.getPropertyPath()) {
            field = node.getName();
        }
        return field;
    }

    private static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /** Field-level validation detail. Carries the field name and reason, never the submitted value. */
    public record FieldViolation(String field, String reason) {
    }
}
