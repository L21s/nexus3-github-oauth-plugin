FROM maven:3.5.2 as builder
MAINTAINER matt.brewster@base2s.com
COPY . /build
WORKDIR /build
RUN mvn clean package


FROM sonatype/nexus3:3.12.0
ARG VERSION=2.0.1

USER root
RUN mkdir -p "/opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/${VERSION}/"
COPY --from=builder "/build/target/nexus3-github-oauth-plugin-${VERSION}.jar" "/opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/${VERSION}/"
COPY --from=builder "/build/target/feature/feature.xml" "/opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/${VERSION}/nexus3-github-oauth-plugin-${VERSION}-features.xml"
COPY --from=builder /build/pom.xml "/opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/${VERSION}/nexus3-github-oauth-plugin-${VERSION}.pom"
RUN echo "<?xml version=\"1.0\" encoding=\"UTF-8\"?><metadata><groupId>com.larscheidschmitzhermes</groupId><artifactId>nexus3-github-oauth-plugin</artifactId><versioning><release>${VERSION}</release><versions><version>${VERSION}</version></versions><lastUpdated>20170630132608</lastUpdated></versioning></metadata>" > /opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/maven-metadata-local.xml
RUN echo "mvn\:com.larscheidschmitzhermes/nexus3-github-oauth-plugin/${VERSION} = 200" >> /opt/sonatype/nexus/etc/karaf/startup.properties

USER nexus
