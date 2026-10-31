package co.com.nequi.ticketing.api;

import co.com.nequi.ticketing.api.config.AdminKey;
import co.com.nequi.ticketing.api.dto.ComplimentaryRequest;
import co.com.nequi.ticketing.api.dto.TicketView;
import co.com.nequi.ticketing.usecase.grantcomplimentary.GrantComplimentaryTicketsUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
public class ComplimentaryHandler {

    static final String ADMIN_KEY = "X-Admin-Key";

    private final GrantComplimentaryTicketsUseCase grantComplimentaryTicketsUseCase;
    private final AdminKey adminKey;

    public ComplimentaryHandler(GrantComplimentaryTicketsUseCase grantComplimentaryTicketsUseCase, AdminKey adminKey) {
        this.grantComplimentaryTicketsUseCase = grantComplimentaryTicketsUseCase;
        this.adminKey = adminKey;
    }

    // The admin key is checked before the body is read
    public Mono<ServerResponse> grant(ServerRequest request) {
        return Mono.fromRunnable(() -> requireAdmin(request))
                .then(Requests.requiredBody(request, ComplimentaryRequest.class))
                .flatMap(body -> grantComplimentaryTicketsUseCase.execute(request.pathVariable("eventId"), body.quantity()))
                .flatMap(granted -> ServerResponse.ok().bodyValue(granted.stream().map(TicketView::of).toList()))
                .onErrorResume(ApiErrors::toResponse);
    }

    private void requireAdmin(ServerRequest request) {
        if (!adminKey.matches(request.headers().firstHeader(ADMIN_KEY))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "a valid " + ADMIN_KEY + " header is required");
        }
    }
}
