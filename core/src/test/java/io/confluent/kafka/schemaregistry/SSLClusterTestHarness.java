package io.confluent.kafka.schemaregistry;

import io.confluent.kafka.schemaregistry.avro.AvroCompatibilityLevel;
import kafka.server.KafkaConfig;
import kafka.utils.TestUtils;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.network.Mode;
import org.apache.kafka.common.protocol.SecurityProtocol;
import org.apache.kafka.test.TestSslUtils;
import scala.Option;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static io.confluent.kafka.schemaregistry.storage.StoreUtils.printDebugMessageForKeystore;

public class SSLClusterTestHarness extends ClusterTestHarness {
  public Map<String, Object> clientSslConfigs;

  public SSLClusterTestHarness() {
    super(DEFAULT_NUM_BROKERS);
  }

  protected SecurityProtocol getSecurityProtocol() {
    return SecurityProtocol.SSL;
  }

  protected KafkaConfig getKafkaConfig(int brokerId) {
    File trustStoreFile;
    try {
      trustStoreFile = File.createTempFile("SSLClusterTestHarness-truststore", ".jks");
    } catch (IOException ioe) {
      throw new RuntimeException("Unable to create temporary file for the truststore.");
    }
    final Option<File> trustStoreFileOption = scala.Option.apply(trustStoreFile);
    final Option<SecurityProtocol> sslInterBrokerSecurityProtocol = scala.Option.apply(SecurityProtocol.SSL);
    Properties props = TestUtils.createBrokerConfig(
            brokerId, zkConnect, false, false, TestUtils.RandomPort(), sslInterBrokerSecurityProtocol,
            trustStoreFileOption, false, false, TestUtils.RandomPort(), true, TestUtils.RandomPort(), false,
            TestUtils.RandomPort(), Option.<String>empty());

    // setup client SSL. Needs to happen before the broker is initialized, because the client's cert
    // needs to be added to the broker's trust store.
    Map<String, Object> sslConfigs;
    try {
      this.clientSslConfigs = TestSslUtils.createSslConfig(true, true, Mode.CLIENT,
              trustStoreFile, "client", "localhost");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    props.setProperty("auto.create.topics.enable", "true");
    props.setProperty("num.partitions", "1");
    if (requireSSLClientAuth()) {
      props.setProperty("ssl.client.auth", "required");
    }
    // We *must* override this to use the port we allocated (Kafka currently allocates one port
    // that it always uses for ZK
    props.setProperty("zookeeper.connect", this.zkConnect);

    // TODO: delete this. Debugging.
    System.out.println("In SSL Cluster Test Harness:");
    printDebugMessageForKeystore(props.getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG), "TrustStorePassword");
    System.out.println("----");
    printDebugMessageForKeystore(props.getProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG), "ServerPassword");

    return KafkaConfig.fromProps(props);
  }

  protected boolean requireSSLClientAuth() {
    return true;
  }
}
