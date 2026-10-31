package co.com.nequi.ticketing.api;

import co.com.nequi.ticketing.api.dto.ErrorResponse;
import co.com.nequi.ticketing.model.exception.BusinessException;
import co.com.nequi.ticketing.model.exception.ConcurrencyConflictException;
import co.com.nequi.ticketing.model.exception.EventNotFoundException;
import co.com.nequi.ticketing.model.exception.InvalidStateTransitionException;
import co.com.nequi.ticketing.model.exception.OrderNotFoundException;
import co.com.nequi.ticketing.model.exception.ReservationExpiredException;
import co.com.nequi.ticketing.model.exception.TicketNotOwnedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

final class ApiErrors {

    private static final Logger log = LogManager.getLogger(ApiErrors.class);

    private ApiErrors() {
    }

    // Unexpected errors never leak internal details to the client
    static Mono<ServerResponse> toResponse(Throwable error) {
        HttpStatus status = statusOf(error);
        String message = status.is5xxServerError() ? "Unexpected error" : error.getMessage();
        if (status.is5xxServerError()) {
            log.error("Unexpected error handling the request", error);
        }
        return ServerResponse.status(status).bodyValue(new ErrorResponse(status.value(), message));
    }

    private static HttpStatus statusOf(Throwable error) {
        return switch (error) {
            case BusinessException business -> statusOf(business);
            case ConcurrencyConflictException _ -> HttpStatus.CONFLICT;
            case IllegalArgumentException _ -> HttpStatus.BAD_REQUEST;
            case ResponseStatusException web -> HttpStatus.valueOf(web.getStatusCode().value());
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    // Exhaustive over the sealed hierarchy: a new business error does not compile until it gets a status
    private static HttpStatus statusOf(BusinessException error) {
        return switch (error) {
            case EventNotFoundException _, OrderNotFoundException _ -> HttpStatus.NOT_FOUND;
            case InvalidStateTransitionException _, TicketNotOwnedException _, ReservationExpiredException _ ->
                    HttpStatus.CONFLICT;
        };
    }
}
