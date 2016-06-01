/**
 * Copyright 2016 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.confluent.kafka.schemaregistry;

import kafka.security.minikdc.MiniKdc;
import kafka.server.KafkaConfig;
import kafka.utils.JaasTestUtils;
import kafka.utils.TestUtils;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.protocol.SecurityProtocol;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.collection.Seq;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;

public class SASLClusterTestHarness extends ClusterTestHarness {
  public static final String JAAS_CONF = "java.security.auth.login.config";

  private static MiniKdc kdc;
  private static File kdcHome;
  private static File jaasFile;

  private static final Logger log = LoggerFactory.getLogger(SASLClusterTestHarness.class);

  public SASLClusterTestHarness() {
    super(DEFAULT_NUM_BROKERS);
  }

  @Override
  protected SecurityProtocol getSecurityProtocol() {
    return SecurityProtocol.SASL_PLAINTEXT;
  }

  @BeforeClass
  public static void setUpKdc() throws Exception {
    Properties kdcProps = MiniKdc.createConfig();
    kdcHome = Files.createTempDirectory("mini-kdc").toFile();
    log.info("Using KDC home: " + kdcHome.getAbsolutePath());
    kdc = new MiniKdc(kdcProps, kdcHome);
    kdc.start();

    // create Kerberos principals.
    File zkServerKeytab = createPrincipal("zookeeper-", "zookeeper/localhost");
    File kafkaKeytab = createPrincipal("kafka-", "kafka/localhost");
    File schemaRegistryKeytab = createPrincipal("schema-registry-",
            "schema-registry/localhost");

    // build and write the JAAS file.
    JaasTestUtils.JaasSection serverSection = createJaasSection(zkServerKeytab,
            "zookeeper/localhost@EXAMPLE.COM", "Server", "zookeeper");
    JaasTestUtils.JaasSection clientSection = createJaasSection(kafkaKeytab,
            "kafka/localhost@EXAMPLE.COM", "Client", "zookeeper");
    JaasTestUtils.JaasSection kafkaServerSection = createJaasSection(kafkaKeytab,
            "kafka/localhost@EXAMPLE.COM", "KafkaServer", "kafka");
    JaasTestUtils.JaasSection kafkaClientSection = createJaasSection(schemaRegistryKeytab,
            "schema-registry/localhost@EXAMPLE.COM", "KafkaClient", "kafka");
    // NOTE: there is only one `Client` section in the Jaas configuraiton file. Both the internal embedded Kafka
    // cluster and the schema registry share the same principal. This is required because within the same JVM (eg
    // these tests) one cannot have two sections, each with its own ZooKeeper client SASL credentials.
    jaasFile = File.createTempFile("schema_registry_tests", "_jaas.conf");
    PrintWriter out = new PrintWriter(jaasFile);
    out.println(serverSection.toString());
    out.println(clientSection.toString());
    out.println(kafkaServerSection.toString());
    out.println(kafkaClientSection.toString());
    out.close();
  }

  @Before
  @Override
  public void setUp() throws Exception {
    // TODO: remove storing of value
    String orig = System.setProperty(JAAS_CONF, jaasFile.getAbsolutePath());
    System.out.println("Original value for " + JAAS_CONF + ": " + orig);

    // TODO: delete this. And delete the Scanner import
    Scanner s = new Scanner(jaasFile);
    System.out.println("********************");
    System.out.println("Printing Jaas file:");
    System.out.println("********************");
    while (s.hasNextLine()) {
      System.out.println("\t" + s.nextLine());
    }
    s.close();

    // don't need to set java.security.krb5.conf because the MiniKdc does it.

    // TODO: delete me
    System.setProperty("sun.security.krb5.debug", "true");

    // we *must* set a SASL provider. Frankly, it's unclear why. Perhaps because the ZooKeeper and Kafka test
    // utils don't set this properly? Kafka's tests manually set this, too.
    System.setProperty("zookeeper.authProvider.1", "org.apache.zookeeper.server.auth.SASLAuthenticationProvider");

    super.setUp();
  }

  @After
  @Override
  public void tearDown() throws Exception {
    System.clearProperty(JAAS_CONF);
    System.clearProperty("zookeeper.authProvider.1");
    super.tearDown();
  }

  protected boolean enableKafkaPlaintextEndpoint() { return false; }

  @Override
  protected KafkaConfig getKafkaConfig(int brokerId) {
    final Option<File> trustStoreFileOption = scala.Option.apply(null);
    final Option<SecurityProtocol> saslInterBrokerSecurityProtocol =
            scala.Option.apply(SecurityProtocol.SASL_PLAINTEXT);
    Properties props = TestUtils.createBrokerConfig(
            brokerId, zkConnect, false, false, TestUtils.RandomPort(), saslInterBrokerSecurityProtocol,
            trustStoreFileOption, EMPTY_SASL_PROPERTIES, enableKafkaPlaintextEndpoint(), true, TestUtils.RandomPort(),
            false, TestUtils.RandomPort(),
            false, TestUtils.RandomPort(), Option.<String>empty());

    injectProperties(props);
    props.setProperty("zookeeper.connection.timeout.ms", "30000");
    props.setProperty("sasl.mechanism.inter.broker.protocol", "GSSAPI");
    props.setProperty(SaslConfigs.SASL_ENABLED_MECHANISMS, "GSSAPI");

    return KafkaConfig.fromProps(props);
  }

  private static File createPrincipal(String pathPrefix, String principalNoRealm) throws Exception {
    File keytab = File.createTempFile(pathPrefix, ".keytab");
    Seq<String> principals = scala.collection.JavaConversions.asScalaBuffer(
            Arrays.asList(principalNoRealm)
    ).seq();
    kdc.createPrincipal(keytab, principals);
    return keytab;
  }

  private static JaasTestUtils.JaasSection createJaasSection(File keytab, String principalWithRealm,
                                                             String jaasContextName, String serviceName) {
    final scala.Option<String> serviceNameOption = scala.Option.apply(serviceName);
    JaasTestUtils.Krb5LoginModule krbModule = new JaasTestUtils.Krb5LoginModule(true, true,
            keytab.getAbsolutePath(), principalWithRealm, false, serviceNameOption);
    Seq<JaasTestUtils.JaasModule> jaasModules = scala.collection.JavaConversions.asScalaBuffer(
            Arrays.asList(krbModule.toJaasModule())
    ).seq();
    return new JaasTestUtils.JaasSection(jaasContextName, jaasModules);
  }

  @AfterClass
  public static void tearDownKdc() throws Exception {
    kdc.stop();
    if (!kdcHome.delete()) {
      log.warn("Could not delete the KDC directory.");
    }
  }
}
