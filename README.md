# Elastic Lifeline-Based Global Load Balancer

This project contains the source code for a malleable lifeline-based global load balancer.
The design of the load balancing scheme is derived from the lifeline-based global load balancer first implemented in X10 and which was later extended to support multiple workers per process and [ported to Java](https://github.com/handist/JavaGLB).

The source code presented in this repository extends this scheme to make it malleable, that is, capable of dynamically increasing and decreasing the number of processes used to perform the computation.

## Build instructions

This project requires Java 11 or greater and Maven to compile the library.

This elastic global load balancer relies on a malleable implementation of the APGAS for Java library. This implementation can be found [here](https://github.com/projectwagomu/apgas). You should first install this library in your local Maven repository using the following commands:

```shell
cd ~
git clone https://github.com/projectwagomu/apgas.git apgas
cd apgas
git checkout 0.0.1
mvn install
```

Then, to compile this project, clone this repository and compile it with the following commands:

```shell
cd ~
git clone https://github.com/projectwagomu/lifelineglb.git lifelineglb
cd lifelineglb
mvn package
```

## Launching a computation

A number of demonstrating scripts are provided in the [`bin`](bin) directory.

## License

This software is released under the terms of the [Eclipse Public License v1.0](LICENSE.txt), though it also uses third-party packages with their own licensing terms.

## Publications

- Transparent Resource Elasticity for Task-Based Cluster Environments with Work Stealing [10.1145/3458744.3473361](https://doi.org/10.1145/3458744.3473361)

## Contributors

In alphabetical order:

- Patrick Finnerty
- Takuma Kanzaki
- Jonas Posner
