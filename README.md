# Peergos mobile apps


## Basic Requirements

A list of the basic requirements can be found online in the [Gluon documentation](https://docs.gluonhq.com/#_requirements). Install the Gluon GraalVM build:  https://github.com/gluonhq/graal/releases/tag/gluon-22.1.0.1-Final

## Quick instructions

### Run the sample on JVM/HotSpot:

    mvn gluonfx:run

### Run the sample as a native image:
    apt install libasound2-dev libavcodec-dev libavformat-dev libavutil-dev libfreetype6-dev libgl-dev libglib2.0-dev libgtk-3-dev libpango1.0-dev libxtst-dev

    mvn gluonfx:build gluonfx:nativerun

### Run the sample as a native android image (linux only, remove last two args to just build):

    export GRAALVM_HOME=$PWD/graalvm-svm-java17-linux-gluon-22.1.0.1-Final
    mvn -Pandroid gluonfx:build gluonfx:package gluonfx:install gluonfx:nativerun

### Run the sample as a native iOS image (macOS only, remove last two args to just build):

    export GRAALVM_HOME=$PWD/graalvm-svm-java17-linux-gluon-22.1.0.1-Final/Contents/Home
    mvn -Pios gluonfx:build gluonfx:package gluonfx:install gluonfx:nativerun

## Selected features

This is a list of all the features that were selected when creating the sample:

### JavaFX 18.0.2 Modules

 - javafx-base
 - javafx-web
 - javafx-graphics
 - javafx-controls

### Gluon Features

 - Glisten: build platform independent user interfaces
 - Attach display
 - Attach lifecycle
 - Attach statusbar
 - Attach storage