FROM openjdk:8-jre-alpine
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.home=/data", "-Djenkins.install.runSetupWizard=false", "-jar", "/usr/share/webapps/jenkins/jenkins.war"]
ENV JENKINS_HOME="/data"

# Place Jenkins groovy scripts into the desired location
COPY groovy/* /data/init.groovy.d/

# Add edge [testing/community] repos and install required packages
RUN apk add --no-cache \
    -X http://dl-cdn.alpinelinux.org/alpine/edge/testing \
    -X  http://dl-cdn.alpinelinux.org/alpine/edge/community \
    buildah \
    docker-cli \
    git \
    jenkins \
    podman \
    kubectl