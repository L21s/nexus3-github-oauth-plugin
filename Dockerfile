FROM sonatype/nexus3:3.3.2
USER root
RUN mkdir -p /opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/1.1.0/
COPY target/nexus3-github-oauth-plugin-1.1.0.jar /opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/1.1.0/
COPY target/feature/feature.xml /opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/1.1.0/nexus3-github-oauth-plugin-1.1.0-features.xml
COPY pom.xml /opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/1.1.0/nexus3-github-oauth-plugin-1.1.0.pom
RUN echo '<?xml version="1.0" encoding="UTF-8"?><metadata><groupId>com.larscheidschmitzhermes</groupId><artifactId>nexus3-github-oauth-plugin</artifactId><versioning><release>1.1.0</release><versions><version>1.1.0</version></versions><lastUpdated>20170630132608</lastUpdated></versioning></metadata>' > /opt/sonatype/nexus/system/com/larscheidschmitzhermes/nexus3-github-oauth-plugin/maven-metadata-local.xml
RUN echo "mvn\:com.larscheidschmitzhermes/nexus3-github-oauth-plugin/1.1.0 = 200" >> /opt/sonatype/nexus/etc/karaf/startup.properties