# syntax=docker/dockerfile:1.2

# Copyright (C) 2020 Bosch Software Innovations GmbH
# Copyright (C) 2021 Bosch.IO GmbH
# Copyright (C) 2021 Alliander N.V.
# Copyright (C) 2021 BMW CarIT GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# License-Filename: LICENSE

#------------------------------------------------------------------------
# Use OpenJDK Eclipe Temurin Ubuntu LTS
FROM eclipse-temurin:11-jdk AS build

# Prepare build environment to use ort scripts from here
COPY scripts /etc/scripts

# Set this to a directory containing CRT-files for custom certificates that ORT and all build tools should know about.
ARG CRT_FILES=""
COPY "$CRT_FILES" /tmp/certificates/
RUN /etc/scripts/import_proxy_certs.sh \
    && if [ -n "$CRT_FILES" ]; then \
        /etc/scripts/import_certificates.sh /tmp/certificates/; \
       fi

#------------------------------------------------------------------------
# Ubuntu build toolchain
RUN --mount=type=cache,target=/var/cache/apt --mount=type=cache,target=/var/lib/apt apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        build-essential \
        clang-9 \
        clang++-9 \
        dirmngr \
        clang-9 \
        clang++-9 \
        dpkg-dev \
        git \
        gnupg \
        libbluetooth-dev \
        libbz2-dev \
        libc6-dev \
        libexpat1-dev \
        libffi-dev \
        libgmp-dev \
        libgdbm-dev \
        liblzma-dev \
        libmpdec-dev \
        libncursesw5-dev \
        libreadline-dev \
        libsqlite3-dev \
        libssl-dev \
        make \
        netbase \
        ruby-dev \
        tk-dev \
        tzdata \
        uuid-dev \
        unzip \
        xz-utils \
        zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

# Prepare build components to install their modules
RUN mkdir -p /etc/ort/bash_modules

#------------------------------------------------------------------------
# Build ort as a separate component

FROM build as ortbuild

# Set this to the version ORT should report.
ARG ORT_VERSION="DOCKER-SNAPSHOT"

COPY . /usr/local/src/ort
WORKDIR /usr/local/src/ort

# Prepare Gradle
RUN --mount=type=cache,target=/tmp/.gradle/ \
    GRADLE_USER_HOME=/tmp/.gradle/ && \
    scripts/import_proxy_certs.sh && \
    scripts/set_gradle_proxy.sh && \
    sed -i -r 's,(^distributionUrl=)(.+)-all\.zip$,\1\2-bin.zip,' gradle/wrapper/gradle-wrapper.properties && \
    sed -i -r '/distributionSha256Sum=[0-9a-f]{64}/d' gradle/wrapper/gradle-wrapper.properties && \
    ./gradlew --no-daemon --stacktrace -Pversion=$ORT_VERSION :cli:distTar :helper-cli:startScripts

RUN mkdir -p /opt/ort \
    && tar xf /usr/local/src/ort/cli/build/distributions/ort-$ORT_VERSION.tar -C /opt/ort --strip-components 1 \
    && cp -a /usr/local/src/ort/scripts/*.sh /opt/ort/bin/ \
    && cp -a /usr/local/src/ort/helper-cli/build/scripts/orth /opt/ort/bin/ \
    && cp -a /usr/local/src/ort/helper-cli/build/libs/helper-cli-*.jar /opt/ort/lib/

#------------------------------------------------------------------------
# PYTHON - Build Python as a separate component with pyenv
FROM build as pythonbuild

ARG CONAN_VERSION=1.40.3
ARG PYTHON_VERSION=3.6.15
ARG PYTHON_VIRTUALENV_VERSION=15.1.0
ARG PIPTOOL_VERSION=21.3.1

ENV PYENV_ROOT=/opt/python
RUN curl -L https://github.com/pyenv/pyenv-installer/raw/master/bin/pyenv-installer | bash
ENV PATH=/opt/python/bin:$PATH
# Python 3.6.x series has problems with alignment with modern GCC
# As we don not want patch Python versions we use Clang as compiler
RUN CC=clang-9 CXX=clang++9 pyenv install "${PYTHON_VERSION}"
RUN pyenv global "${PYTHON_VERSION}"
ENV PATH=/opt/python/shims:$PATH
RUN pip install -U \
    conan=="${CONAN_VERSION}" \
    pip=="${PIPTOOL_VERSION}" \
    pipenv \
    Mercurial \
    virtualenv=="${PYTHON_VIRTUALENV_VERSION}" \
    wheel

COPY docker/python.sh /etc/ort/bash_modules

#------------------------------------------------------------------------
# Main container
FROM eclipse-temurin:11-jre

RUN  --mount=type=cache,target=/var/cache/apt --mount=type=cache,target=/var/lib/apt apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        cvs \
        curl \
        gnupg \
        libarchive-tools \
        openssl \
        unzip

# ORT
COPY --from=ortbuild /opt/ort /opt/ort
COPY docker/bash_bootstrap.sh /etc/ort/bash_bootstrap.sh
COPY docker/ort-wrapper.sh /usr/bin/ort
COPY docker/ort-wrapper.sh /usr/bin/orth
RUN chmod 755 /usr/bin/ort

# Python
COPY --from=pythonbuild /opt/python /opt/python
COPY --from=pythonbuild /etc/ort/bash_modules/python.sh /etc/ort/bash_modules/

ENV \
    # Package manager versions.
    BOWER_VERSION=1.8.8 \
    COMPOSER_VERSION=1.10.1-1 \
    CONAN_VERSION=1.40.3 \
    GO_DEP_VERSION=0.5.4 \
    GO_VERSION=1.16.5 \
    HASKELL_STACK_VERSION=2.1.3 \
    NPM_VERSION=7.20.6 \
    SBT_VERSION=1.3.8 \
    YARN_VERSION=1.22.10 \
    # SDK versions.
    ANDROID_SDK_VERSION=6858069 \
    # Installation directories.
    ANDROID_HOME=/opt/android-sDockerfiledk \
    GOPATH=$HOME/go

ENV DEBIAN_FRONTEND=noninteractive \
    PATH="$PATH:$HOME/.local/bin:$GOPATH/bin:/opt/go/bin:$GEM_PATH/bin"

# Apt install commands.
RUN --mount=type=cache,target=/var/cache/apt --mount=type=cache,target=/var/lib/apt \
    apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates gnupg software-properties-common && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list && \
    curl -ksS "https://keyserver.ubuntu.com/pks/lookup?op=get&options=mr&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key adv --import - && \
    curl -sL https://deb.nodesource.com/setup_16.x | bash - && \
    add-apt-repository -y ppa:git-core/ppa && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        # Install VCS tools (no specific versions required here).
        cvs \
        git \
        gnupg \
        subversion \
        cargo \
        composer=$COMPOSER_VERSION \
        nodejs \
        openssl \
        sbt=$SBT_VERSION \
    && \
    rm -rf /var/lib/apt/lists/*

# Set this to a directory containing CRT-files for custom certificates that ORT and all build tools should know about.
ARG CRT_FILES=""
COPY "$CRT_FILES" /tmp/certificates/

# Custom install commands.
ARG SCANCODE_VERSION="3.2.1rc2"
RUN /opt/ort/bin/import_proxy_certs.sh && \
    if [ -n "$CRT_FILES" ]; then \
      /opt/ort/bin/import_certificates.sh /tmp/certificates/; \
    fi && \
    # Install VCS tools (no specific versions required here).
    curl -ksS https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo && \
    chmod a+x /usr/local/bin/repo && \
    # Install package managers (in versions known to work).
    npm install --global npm@$NPM_VERSION bower@$BOWER_VERSION yarn@$YARN_VERSION && \
    # Install golang in order to have `go mod` as package manager.
    curl -ksSO https://dl.google.com/go/go$GO_VERSION.linux-amd64.tar.gz && \
    tar -C /opt -xzf go$GO_VERSION.linux-amd64.tar.gz && \
    rm go$GO_VERSION.linux-amd64.tar.gz && \
    mkdir -p $GOPATH/bin && \
    curl -ksS https://raw.githubusercontent.com/golang/dep/v$GO_DEP_VERSION/install.sh | sh && \
    curl -ksS https://raw.githubusercontent.com/commercialhaskell/stack/v$HASKELL_STACK_VERSION/etc/scripts/get-stack.sh | sh && \
    # Install SDKs required for analysis.
    curl -Os https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip && \
    unzip -q commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip -d $ANDROID_HOME && \
    rm commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip && \
    PROXY_HOST_AND_PORT=${https_proxy#*://} && \
    if [ -n "$PROXY_HOST_AND_PORT" ]; then \
        # While sdkmanager uses HTTPS by default, the proxy type is still called "http".
        SDK_MANAGER_PROXY_OPTIONS="--proxy=http --proxy_host=${PROXY_HOST_AND_PORT%:*} --proxy_port=${PROXY_HOST_AND_PORT##*:}"; \
    fi && \
    yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager $SDK_MANAGER_PROXY_OPTIONS --sdk_root=$ANDROID_HOME "platform-tools" && \
    # Install 'CocoaPods'. As https://github.com/CocoaPods/CocoaPods/pull/10609 is needed but not yet released.
    curl -ksSL https://github.com/CocoaPods/CocoaPods/archive/9461b346aeb8cba6df71fd4e71661688138ec21b.tar.gz | \
        tar -zxC . && \
        (cd CocoaPods-9461b346aeb8cba6df71fd4e71661688138ec21b && \
            gem build cocoapods.gemspec && \
            gem install cocoapods-1.10.1.gem \
        ) && \
        rm -rf CocoaPods-9461b346aeb8cba6df71fd4e71661688138ec21b && \
    # Add scanners (in versions known to work).
    curl -ksSL https://github.com/nexB/scancode-toolkit/archive/v$SCANCODE_VERSION.tar.gz | \
        tar -zxC /usr/local && \
        # Trigger ScanCode configuration for Python 3 and reindex licenses initially.
        PYTHON_EXE=/usr/bin/python3 /usr/local/scancode-toolkit-$SCANCODE_VERSION/scancode --reindex-licenses && \
        chmod -R o=u /usr/local/scancode-toolkit-$SCANCODE_VERSION && \
        ln -s /usr/local/scancode-toolkit-$SCANCODE_VERSION/scancode /usr/local/bin/scancode


ENTRYPOINT ["/usr/bin/ort"]
