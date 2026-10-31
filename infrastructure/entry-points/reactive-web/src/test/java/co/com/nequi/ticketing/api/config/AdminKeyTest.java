package co.com.nequi.ticketing.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminKeyTest {

    @Test
    void shouldAcceptOnlyTheConfiguredKey() {
        AdminKey adminKey = new AdminKey("admin-secret");

        assertTrue(adminKey.matches("admin-secret"));
        assertFalse(adminKey.matches("admin-secre"));
        assertFalse(adminKey.matches(null));
    }

    @Test
    void shouldDisableAdminOperationsWhenNoKeyIsConfigured() {
        AdminKey adminKey = new AdminKey("");

        assertFalse(adminKey.matches(""));
        assertFalse(adminKey.matches("anything"));
    }
}
