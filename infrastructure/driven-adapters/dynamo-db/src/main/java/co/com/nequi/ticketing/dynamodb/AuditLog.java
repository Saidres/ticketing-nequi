package co.com.nequi.ticketing.dynamodb;

import co.com.nequi.ticketing.model.order.Order;
import co.com.nequi.ticketing.model.ticket.Ticket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

// Audit trail of every persisted state transition: logged only after DynamoDB accepted the write
final class AuditLog {

    private static final Logger log = LogManager.getLogger("audit.transitions");

    private AuditLog() {
    }

    static void committed(Order order, List<Ticket> tickets) {
        log.info("order={} status={} version={} customer={} tickets=[{}]", order.id(), order.status(),
                order.version(), order.customerId(), tickets.stream()
                        .map(ticket -> ticket.id() + ":" + ticket.status() + ":v" + ticket.version())
                        .collect(Collectors.joining(",")));
    }
}
