FROM maven:3-jdk-8 as builder
MAINTAINER matt.brewster@base2s.com
COPY . /build
WORKDIR /build
RUN mvn clean package

# patch log4j vulnerability
ENV LOG4J_FORMAT_MSG_NO_LOOKUPS="true"

FROM sonatype/nexus3:latest
USER root
COPY --from=builder /build/target/nexus3-github-oauth-plugin-*.kar /opt/sonatype/nexus/deploy
USER nexus
