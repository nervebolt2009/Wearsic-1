FROM mcr.microsoft.com/devcontainers/base:ubuntu-22.04

ENV DEBIAN_FRONTEND=noninteractive

# Set Environment Variables for Java and Android SDK
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV ANDROID_HOME=/usr/local/android-sdk
ENV ANDROID_SDK_ROOT=/usr/local/android-sdk
ENV PATH="${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/34.0.0:${PATH}"

# Install Java 17 and essential system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk \
    wget \
    unzip \
    curl \
    git \
    zip \
    libstdc++6 \
    lib32z1 \
    libbz2-1.0 \
    && rm -rf /var/lib/apt/lists/*

# Download and set up Android Command Line Tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept SDK licenses and pre-install Wear OS 5+ SDK packages (API 34 and 35)
RUN yes | sdkmanager --licenses && \
    sdkmanager --update && \
    sdkmanager \
      "platforms;android-34" \
      "platforms;android-35" \
      "build-tools;34.0.0" \
      "build-tools;35.0.0" \
      "platform-tools" \
      "cmdline-tools;latest"

# Grant non-root user 'vscode' permissions over the Android SDK directory
RUN chown -R vscode:vscode ${ANDROID_HOME}

USER vscode
