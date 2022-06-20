package com.provectus.kafka.ui.serdes.builtin.sr;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.provectus.kafka.ui.serde.api.DeserializeResult;
import com.provectus.kafka.ui.serde.api.Serde;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import net.bytebuddy.utility.RandomString;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaRegistrySerdeTest {

  private final MockSchemaRegistryClient registryClient = new MockSchemaRegistryClient();

  private SchemaRegistrySerde serde;

  @BeforeEach
  void init() {
    serde = new SchemaRegistrySerde();
    serde.configure(List.of("wontbeused"), registryClient, "%s-key", "%s-value");
  }

  @Test
  void returnsSchemaDescriptionIfSchemaRegisteredInSR() throws RestClientException, IOException {
    String topic = "test";
    registryClient.register(topic + "-key", new AvroSchema("{ \"type\": \"int\" }"));
    registryClient.register(topic + "-value", new AvroSchema("{ \"type\": \"float\" }"));

    assertThat(serde.getSchema(topic, Serde.Type.KEY)).isPresent();
    assertThat(serde.getSchema(topic, Serde.Type.VALUE)).isPresent();
  }

  @Test
  void returnsEmptyDescriptorIfSchemaNotRegisteredInSR() {
    String topic = "test";
    assertThat(serde.getSchema(topic, Serde.Type.KEY)).isEmpty();
    assertThat(serde.getSchema(topic, Serde.Type.VALUE)).isEmpty();
  }

  @Test
  void serialize() throws RestClientException, IOException {
    AvroSchema schema = new AvroSchema(
        "{"
            + "  \"type\": \"record\","
            + "  \"name\": \"TestAvroRecord1\","
            + "  \"fields\": ["
            + "    {"
            + "      \"name\": \"field1\","
            + "      \"type\": \"string\""
            + "    },"
            + "    {"
            + "      \"name\": \"field2\","
            + "      \"type\": \"int\""
            + "    }"
            + "  ]"
            + "}"
    );
    String jsonValueForSchema = "{ \"field1\":\"testStr\", \"field2\": 123 }";
    String topic = "test";

    int schemaId = registryClient.register(topic + "-value", schema);
    byte[] serialized = serde.serializer(topic, Serde.Type.VALUE).serialize(topic, jsonValueForSchema);
    byte[] expected = schemaRegistryBytes(schemaId, jsonValueForSchema, schema);
    assertThat(serialized).isEqualTo(expected);
  }

  @Test
  void deserializeReturnsJsonAvroMsgJsonRepresentation() throws RestClientException, IOException {
    AvroSchema schema = new AvroSchema(
        "{"
            + "  \"type\": \"record\","
            + "  \"name\": \"TestAvroRecord1\","
            + "  \"fields\": ["
            + "    {"
            + "      \"name\": \"field1\","
            + "      \"type\": \"string\""
            + "    },"
            + "    {"
            + "      \"name\": \"field2\","
            + "      \"type\": \"int\""
            + "    }"
            + "  ]"
            + "}"
    );
    String jsonValueForSchema = "{ \"field1\":\"testStr\", \"field2\": 123 }";

    String topic = "test";
    int schemaId = registryClient.register(topic + "-value", schema);

    byte[] data = schemaRegistryBytes(schemaId, jsonValueForSchema, schema);
    var result = serde.deserializer(topic, Serde.Type.VALUE).deserialize(topic, null, data);

    assertJsonsEqual(jsonValueForSchema, result.getResult());
    assertThat(result.getType()).isEqualTo(DeserializeResult.Type.JSON);
    assertThat(result.getAdditionalProperties())
        .contains(Map.entry("type", "AVRO"))
        .contains(Map.entry("schemaId", schemaId));
  }

  @Test
  void canDeserializeReturnsTrueAlways() {
    String topic = RandomString.make(10);
    assertThat(serde.canDeserialize(topic, Serde.Type.KEY)).isTrue();
    assertThat(serde.canDeserialize(topic, Serde.Type.VALUE)).isTrue();
  }

  private void assertJsonsEqual(String expected, String actual) throws JsonProcessingException {
    var mapper = new JsonMapper();
    assertThat(mapper.readTree(actual)).isEqualTo(mapper.readTree(expected));
  }

  private byte[] schemaRegistryBytes(int schemaId, String json, AvroSchema schema) {
    return bytesWithMagicByteAndSchemaId(schemaId, jsonToAvro(json, schema));
  }

  private byte[] bytesWithMagicByteAndSchemaId(int schemaId, byte[] body) {
    return ByteBuffer.allocate(1 + 4 + body.length)
        .put((byte) 0)
        .putInt(schemaId)
        .put(body)
        .array();
  }

  @SneakyThrows
  private byte[] jsonToAvro(String json, AvroSchema schema) {
    GenericDatumWriter<Object> writer = new GenericDatumWriter<>(schema.rawSchema());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Encoder encoder = EncoderFactory.get().binaryEncoder(output, null);
    writer.write(AvroSchemaUtils.toObject(json, schema), encoder);
    encoder.flush();
    return output.toByteArray();
  }

}