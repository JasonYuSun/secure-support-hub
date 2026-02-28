output "cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "api_service_name" {
  value = aws_ecs_service.api.name
}

output "web_service_name" {
  value = aws_ecs_service.web.name
}

output "api_task_family" {
  value = aws_ecs_task_definition.api.family
}

output "web_task_family" {
  value = aws_ecs_task_definition.web.family
}
