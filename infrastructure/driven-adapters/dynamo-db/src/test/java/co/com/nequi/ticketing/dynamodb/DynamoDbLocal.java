package co.com.nequi.ticketing.dynamodb;

import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.net.URI;
import java.util.UUID;

import static co.com.nequi.ticketing.dynamodb.ItemMappers.EVENT_ID;
import static co.com.nequi.ticketing.dynamodb.ItemMappers.EVENT_STATUS;
import static co.com.nequi.ticketing.dynamodb.ItemMappers.ORDER_ID;
import static co.com.nequi.ticketing.dynamodb.ItemMappers.RESERVED_UNTIL;
import static co.com.nequi.ticketing.dynamodb.ItemMappers.SHUFFLE;
import static co.com.nequi.ticketing.dynamodb.ItemMappers.STATUS;
import static co.com.nequi.ticketing.dynamodb.ItemMappers.TICKET_ID;

// One DynamoDB Local container and client for the whole test run; every test gets its own uniquely named tables
final class DynamoDbLocal {

    private static final GenericContainer<?> CONTAINER =
            new GenericContainer<>("amazon/dynamodb-local:2.6.1").withExposedPorts(8000);
    private static DynamoDbAsyncClient client;

    private DynamoDbLocal() {
    }

    static synchronized DynamoDbAsyncClient client() {
        if (client == null) {
            CONTAINER.start();
            client = newClient();
        }
        return client;
    }

    private static DynamoDbAsyncClient newClient() {
        return DynamoDbAsyncClient.builder()
                .endpointOverride(URI.create("http://%s:%d".formatted(CONTAINER.getHost(), CONTAINER.getMappedPort(8000))))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build();
    }

    static DynamoDbTables createTables(DynamoDbAsyncClient client) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        DynamoDbTables tables = new DynamoDbTables("events-" + suffix, "tickets-" + suffix, "orders-" + suffix);
        client.createTable(events(tables.events())).join();
        client.createTable(tickets(tables.tickets())).join();
        client.createTable(orders(tables.orders())).join();
        return tables;
    }

    private static CreateTableRequest events(String name) {
        return CreateTableRequest.builder()
                .tableName(name)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attribute(EVENT_ID, ScalarAttributeType.S))
                .keySchema(hash(EVENT_ID))
                .build();
    }

    private static CreateTableRequest tickets(String name) {
        return CreateTableRequest.builder()
                .tableName(name)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attribute(TICKET_ID, ScalarAttributeType.S),
                        attribute(EVENT_STATUS, ScalarAttributeType.S),
                        attribute(SHUFFLE, ScalarAttributeType.N),
                        attribute(ORDER_ID, ScalarAttributeType.S))
                .keySchema(hash(TICKET_ID))
                .globalSecondaryIndexes(
                        index(DynamoDbTables.TICKETS_BY_EVENT_STATUS, hash(EVENT_STATUS), range(SHUFFLE)),
                        index(DynamoDbTables.TICKETS_BY_ORDER, hash(ORDER_ID)))
                .build();
    }

    private static CreateTableRequest orders(String name) {
        return CreateTableRequest.builder()
                .tableName(name)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attribute(ORDER_ID, ScalarAttributeType.S),
                        attribute(STATUS, ScalarAttributeType.S),
                        attribute(RESERVED_UNTIL, ScalarAttributeType.N))
                .keySchema(hash(ORDER_ID))
                .globalSecondaryIndexes(
                        index(DynamoDbTables.ORDERS_BY_STATUS_AND_DEADLINE, hash(STATUS), range(RESERVED_UNTIL)))
                .build();
    }

    private static AttributeDefinition attribute(String name, ScalarAttributeType type) {
        return AttributeDefinition.builder().attributeName(name).attributeType(type).build();
    }

    private static KeySchemaElement hash(String name) {
        return KeySchemaElement.builder().attributeName(name).keyType(KeyType.HASH).build();
    }

    private static KeySchemaElement range(String name) {
        return KeySchemaElement.builder().attributeName(name).keyType(KeyType.RANGE).build();
    }

    private static GlobalSecondaryIndex index(String name, KeySchemaElement... keys) {
        return GlobalSecondaryIndex.builder()
                .indexName(name)
                .keySchema(keys)
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }
}
