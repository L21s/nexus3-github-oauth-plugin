FROM maven:3 as builder
MAINTAINER matt.brewster@base2s.com
COPY . /build
WORKDIR /build
RUN mvn versions:set -DnewVersion=docker; mvn clean package

FROM sonatype/nexus3:3.16.1
USER root
RUN mkdir -p /opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/docker/
COPY --from=builder /build/target/nexus3-github-oauth-plugin-docker.jar /opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/docker/
COPY --from=builder /build/target/feature/feature.xml /opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/docker/nexus3-github-oauth-plugin-docker-features.xml
COPY --from=builder /build/pom.xml /opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/docker/nexus3-github-oauth-plugin-docker.pom
RUN echo '<?xml version="1.0" encoding="UTF-8"?><metadata><groupId>com.larscheidschmitzhermes</groupId><artifactId>nexus3-github-oauth-plugin</artifactId><versioning><release>docker</release><versions><version>docker</version></versions><lastUpdated>20170630132608</lastUpdated></versioning></metadata>' > /opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/maven-metadata-local.xml
RUN echo "mvn\:com.larscheidschmitzhermes/nexus3-github-oauth-plugin/docker = 200" >> /opt/sonatype/nexus/etc/karaf/startup.properties
USER nexus
