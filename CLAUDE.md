# CLAUDE.md

This project implements docs/SPEC.md. Read the sections relevant to the current phase before writing code.

## Non-negotiable rules
- Java 11 language level, Spring Boot 2.7.18, javax.* APIs. Do not use Java 17+ features or Jakarta packages.
- Follow the decisions in SPEC.md section 4. Do not add Spring Batch, Lombok, XA transactions, or new frameworks.
- Base package: com.example.reporting. report-pdf-core must not depend on Spring.
- SQL must run unchanged on H2 (MODE=DB2) and IBM DB2 LUW. Put vendor-specific SQL only in db/migration/{vendor} or in config.
- Never load all report rows into memory. Stream rows straight into the PDF writer.
- Never log record contents (names, case numbers). Log only ids, counts, FIPS codes, and statuses.
- Constructor injection only. Immutable value classes with builders. No field injection.
- Every public class in report-pdf-core has Javadoc. Every behavior in SPEC.md acceptance criteria has a test.

## When the spec is silent
Choose the simplest option consistent with SPEC.md and record it in docs/DECISIONS.md (date, decision, reason).

## Build and test
- mvn -q verify runs unit tests, integration tests, Checkstyle, SpotBugs, and JaCoCo.
- Local run: mvn -pl report-service spring-boot:run -Dspring-boot.run.profiles=h2

## Local toolchain
- Build with JDK 11 (Maven Enforcer rejects JDK 18+): `export JAVA_HOME=$(/usr/libexec/java_home -v 11)`.
- Unit tests are `*Test` (Surefire); integration tests are `*IT` (Failsafe, in-memory H2, embedded Artemis).
- Phase status and open items are tracked in docs/DECISIONS.md.
