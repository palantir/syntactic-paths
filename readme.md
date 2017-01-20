[![CircleCI Build Status](https://circleci.com/gh/palantir/syntactic-paths/tree/develop.svg)](https://circleci.com/gh/palantir/syntactic-paths)
[![Download](https://api.bintray.com/packages/palantir/releases/syntactic-paths/images/download.svg) ](https://bintray.com/palantir/releases/syntactic-paths/_latestVersion)

Syntactic Path Library
======================

This library provides an operating-system-independent implementation of Unix-style paths, similar to the NIO `UnixPath`
implementation.

Usage
-----

Simple Gradle setup:

    buildscript {
        repositories {
            jcenter()
        }
    }
    
    dependencies {
        compile 'com.palantir.syntactic-paths:syntactic-paths:0.6.0' 
    }

In Java:

    Path foo = Paths.get("/a", "b").resolve("c");  // represents /a/b/c
    Path bar = foo.relativize(Paths.get("/a"));  // represents b/c

License
-------
This repository is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
