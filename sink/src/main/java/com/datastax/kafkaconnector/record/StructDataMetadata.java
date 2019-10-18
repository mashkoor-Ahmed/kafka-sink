/*
 * Copyright DataStax, Inc.
 *
 *   This software is subject to the below license agreement.
 *   DataStax may make changes to the agreement from time to time,
 *   and will post the amended terms at
 *   https://www.datastax.com/terms/datastax-apache-kafka-connector-license-terms.
 */
package com.datastax.kafkaconnector.record;

import static com.datastax.kafkaconnector.record.StructDataMetadataSupport.getGenericType;

import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.jetbrains.annotations.NotNull;

/** Metadata associated with a {@link StructData}. */
public class StructDataMetadata implements RecordMetadata {
  private final Schema schema;

  public StructDataMetadata(@NotNull Schema schema) {
    this.schema = schema;
  }

  @Override
  public GenericType<?> getFieldType(@NotNull String field, @NotNull DataType cqlType) {
    if (field.equals(RawData.FIELD_NAME)) {
      return GenericType.of(Struct.class);
    }
    Schema fieldType = schema.field(field).schema();
    return getGenericType(fieldType);
  }
}
