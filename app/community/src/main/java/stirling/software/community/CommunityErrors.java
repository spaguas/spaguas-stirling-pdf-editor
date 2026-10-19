package stirling.software.community;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Unique-name conflicts do not expose SQL statements or stored account fields. */
@RestControllerAdvice(assignableTypes = CommunityController.class)
public class CommunityErrors {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ProblemDetail rejected(
            org.springframework.web.server.ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(
                exception.getStatusCode(),
                exception.getReason() == null ? "Request rejected" : exception.getReason());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "A name or membership conflicts with existing data");
    }
}
