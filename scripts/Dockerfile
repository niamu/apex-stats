FROM openjdk:11

RUN apt-get update && \
        apt-get install -y gcc g++ make libz-dev curl rlwrap

# Install Clojure
ENV CLOJURE_VERSION=1.10.1.727
ENV CLOJURE_URL="https://download.clojure.org"
WORKDIR /tmp
RUN wget "${CLOJURE_URL}/install/linux-install-${CLOJURE_VERSION}.sh" \
        && chmod +x linux-install-$CLOJURE_VERSION.sh \
        && ./linux-install-$CLOJURE_VERSION.sh

# Install GraalVM
ENV GRAAL_VERSION=20.2.0
ENV GRAAL_GITHUB_URL="https://github.com/graalvm/graalvm-ce-builds/releases/download"
ENV GRAAL_FILE="graalvm-ce-java11-linux-amd64-${GRAAL_VERSION}.tar.gz"
ENV GRAALVM_HOME="/graalvm/"
WORKDIR /graalvm
RUN curl -OL "${GRAAL_GITHUB_URL}/vm-${GRAAL_VERSION}/${GRAAL_FILE}"
RUN echo "${GRAAL_GITHUB_URL}/vm-${GRAAL_VERSION}/${GRAAL_FILE}"
RUN tar xfz "${GRAAL_FILE}" --strip-components=1
RUN /graalvm/bin/gu install native-image
