FROM maven:3.9-eclipse-temurin-11

# Set up Maven environment for Atlassian plugin development
# The AMPS plugin in pom.xml will handle all Atlassian-specific dependencies
ENV MAVEN_OPTS="-Xmx2048m"

WORKDIR /workspace

# Copy project files
COPY pom.xml .
COPY src ./src

# Pre-download dependencies to improve build performance
RUN mvn dependency:go-offline -B || true

CMD ["bash"]
