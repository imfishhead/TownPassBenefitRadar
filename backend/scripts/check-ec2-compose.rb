require "json"
require "yaml"

root = File.expand_path("../..", __dir__)
compose_path = File.join(root, "docker-compose.yml")
compose = YAML.safe_load(File.read(compose_path), [], [], true, compose_path)

raise "Compose project name must be good-morning" unless compose["name"] == "good-morning"
raise "obsolete Compose version key must be removed" if compose.key?("version")

services = compose.fetch("services")
%w[frontend backend mariadb minio minio-init prometheus grafana loki alloy].each do |service|
  raise "missing EC2 service: #{service}" unless services.key?(service)
end

backend_aliases = services.fetch("backend")
  .fetch("networks")
  .fetch("default")
  .fetch("aliases")
raise "backend must provide api network alias" unless backend_aliases.include?("api")

backend_environment = services.fetch("backend").fetch("environment")
unless backend_environment.include?(
  "SPRING_DATASOURCE_USERNAME=${DB_USERNAME:-good_morning}"
)
  raise "backend must default to the dedicated good_morning database user"
end
%w[AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY].each do |variable|
  entry = backend_environment.find { |item| item.start_with?("#{variable}=") }
  raise "backend must set #{variable} (MinIO credentials)" if entry.nil?
  raise "backend #{variable} must be required (:?), not defaulted" unless entry.include?(":?")
end
unless backend_environment.include?("S3_ENDPOINT=http://minio:9000")
  raise "backend must reach MinIO at http://minio:9000"
end
unless backend_environment.any? { |item| item.start_with?("S3_PUBLIC_ENDPOINT=${S3_PUBLIC_ENDPOINT:-") }
  raise "backend must take S3_PUBLIC_ENDPOINT from .env"
end
unless services.fetch("backend").dig("depends_on", "minio-init", "condition") == "service_completed_successfully"
  raise "backend must wait for minio-init to create the bucket"
end
raise "backend must expose a healthcheck" unless services.fetch("backend").key?("healthcheck")

minio = services.fetch("minio")
minio_ports = minio.fetch("ports", [])
unless minio_ports.all? { |port| port.to_s.start_with?("127.0.0.1:") }
  raise "MinIO must not publish ports beyond EC2 loopback"
end
if minio_ports.any? { |port| port.to_s.end_with?(":9000") }
  raise "MinIO S3 port 9000 must stay private; images go through frontend Nginx"
end
raise "MinIO must expose a healthcheck" unless minio.key?("healthcheck")
%w[MINIO_ROOT_USER MINIO_ROOT_PASSWORD].each do |variable|
  unless minio.fetch("environment").fetch(variable).include?(":?")
    raise "MinIO #{variable} must be required (:?), not defaulted"
  end
end
raise "missing named volume: minio-data" unless compose.fetch("volumes").key?("minio-data")

frontend_environment = services.fetch("frontend").fetch("environment")
unless frontend_environment.include?("S3_URL=http://minio:9000") &&
       frontend_environment.any? { |item| item.start_with?("S3_BUCKET=${AWS_S3_BUCKET:-") }
  raise "frontend must proxy /<bucket>/ to MinIO (S3_URL and S3_BUCKET)"
end
proxy_script = File.read(File.join(root, "GoodMorning/docker/10-api-proxy.sh"))
unless proxy_script.include?("limit_except GET HEAD")
  raise "frontend Nginx must only allow GET/HEAD on the public MinIO route"
end

mariadb_environment = services.fetch("mariadb").fetch("environment")
unless mariadb_environment.fetch("MARIADB_USER") == "${DB_USERNAME:-good_morning}"
  raise "MariaDB must not default MARIADB_USER to root"
end

prometheus_mounts = services.fetch("prometheus").fetch("volumes")
unless prometheus_mounts.include?(
  "./backend/monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro"
)
  raise "Prometheus must mount the backend monitoring config"
end
unless services.fetch("prometheus").dig("depends_on", "backend", "condition") == "service_healthy"
  raise "Prometheus must wait for the backend healthcheck"
end
raise "Prometheus must expose a healthcheck" unless services.fetch("prometheus").key?("healthcheck")

grafana_mounts = services.fetch("grafana").fetch("volumes")
required_grafana_mounts = [
  "./backend/monitoring/grafana/provisioning/datasources:/etc/grafana/provisioning/datasources:ro",
  "./backend/monitoring/grafana/provisioning/dashboards:/etc/grafana/provisioning/dashboards:ro",
  "./backend/monitoring/grafana/dashboards:/var/lib/grafana/dashboards:ro"
]
missing_grafana_mounts = required_grafana_mounts - grafana_mounts
unless missing_grafana_mounts.empty?
  raise "Grafana provisioning mounts missing: #{missing_grafana_mounts.join(", ")}"
end
raise "Grafana must expose a healthcheck" unless services.fetch("grafana").key?("healthcheck")

alloy_mounts = services.fetch("alloy").fetch("volumes")
raise "Alloy must mount the Docker socket read-only" unless alloy_mounts.include?(
  "/var/run/docker.sock:/var/run/docker.sock:ro"
)

%w[loki-data alloy-data].each do |volume|
  raise "missing named volume: #{volume}" unless compose.fetch("volumes").key?(volume)
end

dashboard_paths = Dir[File.join(
  root,
  "backend/monitoring/grafana/dashboards/*.json"
)]
log_expressions = dashboard_paths.flat_map do |dashboard_path|
  dashboard = JSON.parse(File.read(dashboard_path))
  dashboard.fetch("panels").flat_map do |panel|
    panel.fetch("targets", []).map { |target| target.fetch("expr", "") }
  end
end.select { |expression| expression.include?("compose_service") }

raise "Grafana dashboards must contain log expressions" if log_expressions.empty?
unless log_expressions.all? { |expression| expression.include?('compose_service=~"api|backend"') }
  raise "Grafana log expressions must support api and backend service labels"
end

dockerfile = File.read(File.join(root, "backend/Dockerfile"))
raise "runtime image build must copy openapi.yaml" unless dockerfile.include?("COPY openapi.yaml ./")

env_example = File.read(File.join(root, ".env.example"))
%w[
  DB_NAME
  DB_USERNAME
  DB_PASSWORD
  DB_ROOT_PASSWORD
  AWS_REGION
  AWS_S3_BUCKET
  AWS_ACCESS_KEY_ID
  AWS_SECRET_ACCESS_KEY
  S3_PUBLIC_ENDPOINT
  GRAFANA_ROOT_URL
  GRAFANA_ADMIN_PASSWORD
].each do |variable|
  raise ".env.example is missing #{variable}" unless env_example.match?(/^#{variable}=/)
end

gitignore = File.read(File.join(root, ".gitignore"))
raise "root .env must be ignored" unless gitignore.lines.map(&:strip).include?(".env")

runbook_path = File.join(root, "backend/docs/deployment/ec2-compose-alb-runbook.md")
runbook = File.read(runbook_path)
%w[/healthz /actuator/health /grafana/ /api/v1/me /api/swagger-ui/index.html S3_PUBLIC_ENDPOINT minio-data].each do |marker|
  raise "EC2 runbook is missing #{marker}" unless runbook.include?(marker)
end

monitoring_smoke_test = File.read(File.join(root, "backend/scripts/monitoring-smoke-test.sh"))
unless monitoring_smoke_test.include?('compose_service=~"api|backend"')
  raise "monitoring smoke test must support both local and production backend labels"
end

puts "EC2 Compose checks passed: services=#{services.keys.sort.join(",")}"
