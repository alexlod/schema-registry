.. _schemaregistry_security:

Security Overview
-----------------
The Schema Registry currently supports two security features: communication with a secure Kafka cluster over SSL; and API calls over HTTPS. At this time, ZooKeeper security and Kafka SASL authentication are not yet supported.

For more details, check the :ref:`configuration options<schemaregistry_config>`.

Kafka Store
~~~~~~~~~~~
The Schema Registry uses Kafka to persist schemas. The following Kafka security configurations are currently supported:

* SSL encryption
* SSL authentication

ZooKeeper
~~~~~~~~~~~

TODO: Write tests (oy)

TODO: Document that the SASL credentials must be the same as the broker if ZK ACLs are set. An alternative is to turn off ACLs for both kafka and schema registry and use different principals.

TODO: Document the other SASL config requirements -- the java properties, the jaas file, and the zookeeper acl option.
