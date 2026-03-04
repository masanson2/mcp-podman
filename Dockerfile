FROM docker.io/library/eclipse-temurin:21-jre-jammy

# Install podman client so the server can run 'podman' commands inside the container
RUN apt-get update \
    && apt-get install -y --no-install-recommends podman \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/podman-mcp-1.0.0-runner.jar app.jar

# CONTAINER_HOST is injected at runtime via -e to point at the host socket
ENTRYPOINT ["java", "-jar", "app.jar"]
