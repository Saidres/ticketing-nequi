# Same schema as deployment/local/init-aws.sh; on-demand capacity absorbs on-sale spikes without pre-provisioning
resource "aws_dynamodb_table" "events" {
  name                        = "${local.name}-events"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "eventId"
  deletion_protection_enabled = local.prod

  attribute {
    name = "eventId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }
}

resource "aws_dynamodb_table" "tickets" {
  name                        = "${local.name}-tickets"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "ticketId"
  deletion_protection_enabled = local.prod

  attribute {
    name = "ticketId"
    type = "S"
  }

  attribute {
    name = "eventStatus"
    type = "S"
  }

  attribute {
    name = "shuffle"
    type = "N"
  }

  attribute {
    name = "orderId"
    type = "S"
  }

  global_secondary_index {
    name            = "eventStatus-shuffle-index"
    hash_key        = "eventStatus"
    range_key       = "shuffle"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "orderId-index"
    hash_key        = "orderId"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }
}

resource "aws_dynamodb_table" "orders" {
  name                        = "${local.name}-orders"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "orderId"
  deletion_protection_enabled = local.prod

  attribute {
    name = "orderId"
    type = "S"
  }

  attribute {
    name = "status"
    type = "S"
  }

  attribute {
    name = "reservedUntil"
    type = "N"
  }

  global_secondary_index {
    name            = "status-reservedUntil-index"
    hash_key        = "status"
    range_key       = "reservedUntil"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }
}

resource "aws_sqs_queue" "orders_dlq" {
  name                      = "${local.name}-orders-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "orders" {
  name                       = "${local.name}-orders"
  visibility_timeout_seconds = 30
  receive_wait_time_seconds  = 20
  sqs_managed_sse_enabled    = true

  # A message that fails 5 times goes to the DLQ instead of being retried forever
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.orders_dlq.arn
    maxReceiveCount     = 5
  })
}
