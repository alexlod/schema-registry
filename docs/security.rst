.. _schemaregistry_security:

Security Overview
-----------------
The Schema Registry currently only supports communication with a secure Kafka cluster over SSL. At this time, REST API and ZooKeeper security is not yet supported. Kafka SASL authentication is not supported yet either.

Kafka Store
~~~~~~~~~~~
The Schema Registry uses Kafka to persist schemas. The following Kafka security configurations are currently supported:

* SSL encryption
* SSL authentication
