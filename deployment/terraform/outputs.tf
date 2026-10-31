output "api_url" {
  value = "https://${aws_lb.app.dns_name}"
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "orders_queue_url" {
  value = aws_sqs_queue.orders.url
}
