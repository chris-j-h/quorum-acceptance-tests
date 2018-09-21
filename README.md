### Prerequisites

* Java 8
* Maven 3.5.x
* Solidity Compiler

### Writing Tests

* Using [Gauge](https://github.com/getgauge/gauge) test automation framework
* Test Specs are stored in [`src/specs`](src/specs) folder
* Gluecodes are written in Java under [`src/test/java`](src/test/java) folder

### Running Tests

* When `quorum-cloud` is used to provision Quorum Network:
  * Start SOCKS proxy for SSH tunneling. E.g.: listening on port `5000`
    ```
    $ ssh -D 5000 -N -o ServerAliveInterval=30 -i <private_key> ec2-user@<bastion node>
    ```
  * Obtain the nodes metadata from Bastion Node and create a file `config/application-local.yml` with content similar to a sample file in [`config`](config) folder.

### Logging

* Set environment variable: `LOGGING_LEVEL_COM_QUORUM_GAUGE=DEBUG`

------

[![Gauge Badge](https://gauge.org/Gauge_Badge.svg)](https://gauge.org)
