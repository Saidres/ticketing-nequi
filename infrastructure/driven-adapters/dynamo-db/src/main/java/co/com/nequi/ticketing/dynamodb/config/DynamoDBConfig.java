package co.com.nequi.ticketing.dynamodb.config;

import co.com.nequi.ticketing.dynamodb.DynamoDbTables;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;

import java.net.URI;

@Configuration
public class DynamoDBConfig {

    @Bean
    public DynamoDbAsyncClient dynamoDbAsyncClient(@Value("${aws.region}") String region,
                                                   @Value("${aws.dynamodb.endpoint:#{null}}") String endpoint,
                                                   MetricPublisher publisher) {
        DynamoDbAsyncClientBuilder builder = DynamoDbAsyncClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(o -> o.addMetricPublisher(publisher))
                .credentialsProvider(credentials(endpoint));
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    public DynamoDbTables dynamoDbTables(@Value("${aws.dynamodb.tables.events}") String events,
                                         @Value("${aws.dynamodb.tables.tickets}") String tickets,
                                         @Value("${aws.dynamodb.tables.orders}") String orders) {
        return new DynamoDbTables(events, tickets, orders);
    }

    // An explicit endpoint means DynamoDB Local, which accepts any credentials; AWS uses the default chain (IAM role)
    static AwsCredentialsProvider credentials(String endpoint) {
        return endpoint != null
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))
                : DefaultCredentialsProvider.builder().build();
    }
}
