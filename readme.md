[![Build Status](https://travis-ci.org/uschi2000/syntactic-paths.svg)](https://travis-ci.org/uschi2000/syntactic-paths)

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
        compile 'uschi2000:syntactic-paths:0.1.0'
    }

In Java:

    Path foo = Paths.get("/a", "b").resolve("c");  // represents /a/b/c
    Path bar = foo.relativize(Paths.get("/a"));  // represents b/c

License
-------
This repository is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
