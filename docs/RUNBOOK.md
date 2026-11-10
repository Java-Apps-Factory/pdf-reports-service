# Runbook

Written in Phase 6 (SPEC section 18). Until then:

- Build: `mvn -q verify` with JDK 11.
- Local run: `mvn -pl report-service -am install -DskipTests` once, then
  `mvn -pl report-service spring-boot:run -Dspring-boot.run.profiles=h2`.
- Health: `GET /actuator/health`.
- Local database: H2 file at `./data/reportdb` (delete the `data` folder to start clean).
