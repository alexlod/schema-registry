/**
 * Copyright 2014 Confluent Inc.
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
package io.confluent.kafka.schemaregistry.storage;

import io.confluent.kafka.schemaregistry.SSLClusterTestHarness;
import io.confluent.kafka.schemaregistry.storage.exceptions.StoreException;
import io.confluent.kafka.schemaregistry.storage.exceptions.StoreInitializationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class KafkaStoreSSLAuthTest extends SSLClusterTestHarness {
  private static final Logger log = LoggerFactory.getLogger(KafkaStoreSSLAuthTest.class);

  @Before
  public void setup() {
    log.debug("Zk conn url = " + zkConnect);
  }

  @After
  public void teardown() {
    log.debug("Shutting down");
  }

  @Test
  public void testInitialization() {
    KafkaStore<String, String> kafkaStore = StoreUtils.createAndInitSSLKafkaStoreInstance(zkConnect,
            zkClient, clientSslConfigs, true);
    kafkaStore.close();
  }


  @Test
  public void testIncorrectInitialization() {
    KafkaStore<String, String> kafkaStore = StoreUtils.createAndInitSSLKafkaStoreInstance(zkConnect,
            zkClient, clientSslConfigs, true);
    try {
      kafkaStore.init();
      fail("Kafka store repeated initialization should fail");
    } catch (StoreInitializationException e) {
      // this is expected
    }
    kafkaStore.close();
  }

  @Test
  public void testSimplePut() throws InterruptedException {
    KafkaStore<String, String> kafkaStore = StoreUtils.createAndInitSSLKafkaStoreInstance(zkConnect,
            zkClient, clientSslConfigs, true);
    String key = "Kafka";
    String value = "Rocks";
    try {
      kafkaStore.put(key, value);
    } catch (StoreException e) {
      fail("Kafka store put(Kafka, Rocks) operation failed");
    }
    String retrievedValue = null;
    try {
      retrievedValue = kafkaStore.get(key);
    } catch (StoreException e) {
      fail("Kafka store get(Kafka) operation failed");
    }
    assertEquals("Retrieved value should match entered value", value, retrievedValue);
    kafkaStore.close();
  }
}
