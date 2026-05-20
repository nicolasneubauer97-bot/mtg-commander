#!/bin/sh
#
# Gradle start up script for UN*X
#
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

warn () {
    echo "$*"
} >&2

APP_HOME=`pwd -P`

if [ "$APP_HOME" = "" ] ; then
    APP_HOME="."
fi

GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -r "$GRADLE_WRAPPER_JAR" ] ; then
    if ! command -v gradle &> /dev/null; then
        die "ERROR: Gradle wrapper jar not found and gradle not installed."
    fi
    exec gradle "$@"
fi

exec java $DEFAULT_JVM_OPTS -classpath "$GRADLE_WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
