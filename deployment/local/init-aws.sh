#!/bin/sh
# Creates the local AWS resources: the SQS queue with its dead-letter queue (LocalStack) and the DynamoDB tables
set -e

SQS="aws --endpoint-url http://localstack:4566 --region us-east-1 sqs"
DDB="aws --endpoint-url http://dynamodb-local:8000 --region us-east-1 dynamodb"

until $DDB list-tables > /dev/null 2>&1; do
  echo "Waiting for DynamoDB Local..."
  sleep 1
done

$SQS create-queue --queue-name ticketing-orders-dlq
DLQ_ARN=$($SQS get-queue-attributes --queue-url http://localstack:4566/000000000000/ticketing-orders-dlq \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
# After 5 failed receives a message moves to the DLQ instead of being retried forever
$SQS create-queue --queue-name ticketing-orders --attributes \
  "{\"VisibilityTimeout\":\"30\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}"

# Idempotent: re-running `docker compose up` must not fail because a table already exists
table_missing() {
  ! $DDB describe-table --table-name "$1" > /dev/null 2>&1
}

table_missing ticketing-events && $DDB create-table --table-name ticketing-events --billing-mode PAY_PER_REQUEST \
  --attribute-definitions AttributeName=eventId,AttributeType=S \
  --key-schema AttributeName=eventId,KeyType=HASH

table_missing ticketing-tickets && $DDB create-table --table-name ticketing-tickets --billing-mode PAY_PER_REQUEST \
  --attribute-definitions AttributeName=ticketId,AttributeType=S AttributeName=eventStatus,AttributeType=S \
    AttributeName=shuffle,AttributeType=N AttributeName=orderId,AttributeType=S \
  --key-schema AttributeName=ticketId,KeyType=HASH \
  --global-secondary-indexes \
    '[{"IndexName":"eventStatus-shuffle-index","KeySchema":[{"AttributeName":"eventStatus","KeyType":"HASH"},{"AttributeName":"shuffle","KeyType":"RANGE"}],"Projection":{"ProjectionType":"ALL"}},
      {"IndexName":"orderId-index","KeySchema":[{"AttributeName":"orderId","KeyType":"HASH"}],"Projection":{"ProjectionType":"ALL"}}]'

table_missing ticketing-orders && $DDB create-table --table-name ticketing-orders --billing-mode PAY_PER_REQUEST \
  --attribute-definitions AttributeName=orderId,AttributeType=S AttributeName=status,AttributeType=S \
    AttributeName=reservedUntil,AttributeType=N \
  --key-schema AttributeName=orderId,KeyType=HASH \
  --global-secondary-indexes \
    '[{"IndexName":"status-reservedUntil-index","KeySchema":[{"AttributeName":"status","KeyType":"HASH"},{"AttributeName":"reservedUntil","KeyType":"RANGE"}],"Projection":{"ProjectionType":"ALL"}}]'

echo "Local AWS resources ready"
exit 0
