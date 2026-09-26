# API DB connection monitoring

## Data flow

Spring Boot Actuator auto-configures HikariCP's Micrometer integration for the
application's DataSource beans. The MySQL pool is named `mysql`; the optional
PostgreSQL/vector pool is named `postgresql`. The existing pool sizes and connection
timeouts are unchanged. With the vector datasource disabled, only MySQL is monitored.

`CloudWatchMetricsConfig` creates a CloudWatch registry only when
`monitoring.cloudwatch.enabled=true`. It uses the AWS SDK default credentials chain
(the EC2 instance role in production), and publishes directly to CloudWatch every
60 seconds. This does not use the CloudWatch Agent JSON or require a public
Actuator HTTP endpoint. All Actuator web/JMX endpoints are excluded from exposure.

Only `hikaricp.*` meters are sent to this registry. JVM, HTTP and other automatic
meters are not exported to CloudWatch by this configuration. Dimensions include
`application=mirrorsoul-api`, `environment`, `instance`, and Hikari's `pool`.
Use a distinct `METRICS_INSTANCE_ID` per API process if scaling to multiple replicas.

## Deployment

The production GitHub Actions workflow forwards these **production environment
variables** (Settings → Environments → production → Variables):

- `CLOUDWATCH_METRICS_ENABLED=true` to opt in (default: false).
- `METRICS_INSTANCE_ID=i-0c8412eedc10947cf` for the current API instance, or another
  stable unique process identifier (default: `api-1`). This is an app dimension,
  not an automatically detected EC2 InstanceId.

The workflow sets `APP_ENV=production` and uses the existing `AWS_REGION` secret.
Deploy the modified application through the existing deployment process.
For manual deployment, supply the same environment variables to the Java process.
Optional application environment settings are `CLOUDWATCH_METRICS_NAMESPACE`
(default `MirrorSoul/API`) and `CLOUDWATCH_METRICS_STEP` (default `60s`);
the workflow currently uses those defaults.

The application's AWS credentials need `cloudwatch:PutMetricData`. Maintain this
permission in the Terraform project, not this repository. If the application uses
the EC2 role already granted CloudWatchAgentServerPolicy, check that role's policy
and any permissions boundaries; static environment credentials take precedence
over the instance role. CloudWatch HTTPS access is also required.
Custom CloudWatch metrics incur usage charges.

## Dashboard

After deployment and pool initialization, allow a few publish intervals. In Seoul
CloudWatch Classic metrics, choose `MirrorSoul/API`, then the application,
environment, instance and pool dimensions. Add these to `mirrorsoul-api-monitoring`
with separate series for `pool=mysql` and `pool=postgresql`:

| Metric | Meaning | Statistic |
| --- | --- | --- |
| `hikaricp.connections.active.value` | Connections currently borrowed by the app | Maximum |
| `hikaricp.connections.idle.value` | Available idle connections | Average |
| `hikaricp.connections.max.value` | This app pool's configured maximum, not RDS max_connections | Maximum |
| `hikaricp.connections.pending.value` | Threads waiting to borrow a connection | Maximum |
| `hikaricp.connections.timeout.count` | Acquisition timeouts during publishing intervals | Sum |

Micrometer's CloudWatch timer export also provides
`hikaricp.connections.acquire.avg` / `.max` / `.count` for acquisition duration,
and `hikaricp.connections.usage.*` / `hikaricp.connections.creation.*` for connection
usage and physical connection creation. Time units are supplied by the exporter;
check the displayed unit before naming a widget in milliseconds.

Gauges are sampled: a brief spike between samples can be missed even with Maximum.
An active connection is borrowed from the pool, not necessarily executing SQL.
Timeout counters describe pool acquisition failures; they are **not** a complete
counter for authentication failures, SQL errors, broken connections during queries,
or startup failures. Use application logs alongside these metrics. Slow-query
monitoring and a general DB error counter are not implemented by this change.

RDS's `DatabaseConnections` remains on the RDS dashboard and includes other clients
(such as the AI server). This change only instruments the Java API; FastAPI needs
its own implementation in its repository.

## Verification and rollback

`./gradlew test` includes a two-pool H2 test of Boot auto-instrumentation, separate
pool labels, and an exhausted MySQL pool producing a timeout without incrementing
the PostgreSQL timeout counter. A disabled-export test verifies no AWS client is
created. These tests do not send data to AWS or connect to production RDS.

To stop exporting, set `CLOUDWATCH_METRICS_ENABLED=false` and redeploy/restart.
Existing CloudWatch metric history remains available under its retention rules.

References:
- https://docs.spring.io/spring-boot/reference/actuator/metrics.html
- https://docs.micrometer.io/micrometer/reference/implementations/cloudwatch.html
