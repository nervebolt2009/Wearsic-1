FROM mcr.microsoft.com/devcontainers/base:ubuntu-22.04

ENV DEBIAN_FRONTEND=noninteractive

# 1. Java 17, Node.js 22 (LTS), and base build tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless \
    wget \
    unzip \
    curl \
    git \
    build-essential \
    file \
    ca-certificates \
    gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/* \
    && java -version \
    && node -v && npm -v

# 2. Environment paths
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV ANDROID_HOME=/home/vscode/android-sdk
ENV ANDROID_SDK_ROOT=/home/vscode/android-sdk
ENV PATH="${PATH}:${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

# 3. Install freebuff CLI (AI coding agent) globally.
#    The build fails loudly here if the install is broken, instead of
#    failing quietly later when you try to use it.
RUN npm install -g freebuff && freebuff --version

# 4. Android SDK directory, owned by the vscode user
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    chown -R vscode:vscode /home/vscode/android-sdk

USER vscode

# 5. Android command-line tools.
#    If this URL ever 404s (Google deprecates the build number), get the
#    newest one from https://developer.android.com/studio#command-line-tools-only
RUN set -e && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extracted && \
    mkdir -p ${ANDROID_HOME}/cmdline-tools/latest && \
    mv /tmp/cmdline-tools-extracted/cmdline-tools/* ${ANDROID_HOME}/cmdline-tools/latest/ && \
    rm -rf /tmp/cmdline-tools*

# 6. Accept licenses and install Wear OS 5 (API 34/35) + Wear OS 6 (API 36)
#    platforms & build-tools. No error-hiding: if a license or package
#    fails, the image build stops here with a clear message instead of
#    breaking your first Gradle sync.
RUN set -e && \
    yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses && \
    ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
        "platform-tools" \
        "platforms;android-34" \
        "platforms;android-35" \
        "platforms;android-36" \
        "build-tools;34.0.0" \
        "build-tools;35.0.0" \
        "build-tools;36.0.0"

USER root
