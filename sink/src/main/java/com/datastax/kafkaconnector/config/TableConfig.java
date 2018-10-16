/*
 * Copyright DataStax, Inc.
 *
 *   This software is subject to the below license agreement.
 *   DataStax may make changes to the agreement from time to time,
 *   and will post the amended terms at
 *   https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.kafkaconnector.config;

import static com.datastax.kafkaconnector.util.StringUtil.singleQuote;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.google.common.base.Splitter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.jetbrains.annotations.NotNull;

/** Table-specific connector configuration. */
public class TableConfig extends AbstractConfig {
  public static final String MAPPING_OPT = "mapping";
  public static final String TTL_OPT = "ttl";
  static final String CL_OPT = "consistencyLevel";

  private static final String DELETES_ENABLED_OPT = "deletesEnabled";
  private static final String NULL_TO_UNSET_OPT = "nullToUnset";
  private static final Pattern DELIM_PAT = Pattern.compile(", *");

  private final String topicName;
  private final CqlIdentifier keyspace;
  private final CqlIdentifier table;
  private final String mappingString;
  private final Map<CqlIdentifier, CqlIdentifier> mapping;
  private final ConsistencyLevel consistencyLevel;
  private final int ttl;
  private final boolean nullToUnset;
  private final boolean deletesEnabled;

  private TableConfig(
      @NotNull String topicName,
      @NotNull String keyspace,
      @NotNull String table,
      @NotNull Map<String, String> settings) {
    super(makeTableConfigDef(topicName, keyspace, table), settings, false);

    this.topicName = topicName;
    this.keyspace = parseLoosely(keyspace);
    this.table = parseLoosely(table);
    mappingString = getString(getTableSettingPath(topicName, keyspace, table, MAPPING_OPT));
    mapping = parseMappingString(mappingString);
    String clOptName = getTableSettingPath(topicName, keyspace, table, CL_OPT);
    String clString = getString(clOptName);
    try {
      consistencyLevel = DefaultConsistencyLevel.valueOf(clString.toUpperCase());
    } catch (IllegalArgumentException e) {
      // Must be a non-existing enum value.
      throw new ConfigException(
          clOptName,
          singleQuote(clString),
          String.format(
              "valid values include: %s",
              Arrays.stream(DefaultConsistencyLevel.values())
                  .map(DefaultConsistencyLevel::name)
                  .collect(Collectors.joining(", "))));
    }
    ttl = getInt(getTableSettingPath(topicName, keyspace, table, TTL_OPT));
    nullToUnset = getBoolean(getTableSettingPath(topicName, keyspace, table, NULL_TO_UNSET_OPT));
    deletesEnabled =
        getBoolean(getTableSettingPath(topicName, keyspace, table, DELETES_ENABLED_OPT));
  }

  /**
   * Given the attributes of a setting, compute its full name/path.
   *
   * @param topicName name of topic
   * @param keyspace name of keyspace
   * @param table name of table
   * @param setting base name of setting
   * @return full path of the setting in the form "topic.[topicname].[keyspace].[table].[setting]".
   */
  @NotNull
  public static String getTableSettingPath(
      @NotNull String topicName,
      @NotNull String keyspace,
      @NotNull String table,
      @NotNull String setting) {
    return String.format("topic.%s.%s.%s.%s", topicName, keyspace, table, setting);
  }

  @NotNull
  public String getSettingPath(@NotNull String settingName) {
    return getTableSettingPath(topicName, keyspace.asInternal(), table.asInternal(), settingName);
  }

  @NotNull
  public CqlIdentifier getKeyspace() {
    return keyspace;
  }

  @NotNull
  public CqlIdentifier getTable() {
    return table;
  }

  @NotNull
  public String getTopicName() {
    return topicName;
  }

  @NotNull
  public String getKeyspaceAndTable() {
    return String.format("%s.%s", keyspace.asCql(true), table.asCql(true));
  }

  @NotNull
  public Map<CqlIdentifier, CqlIdentifier> getMapping() {
    return mapping;
  }

  @NotNull
  public String getMappingString() {
    return mappingString;
  }

  @NotNull
  public ConsistencyLevel getConsistencyLevel() {
    return consistencyLevel;
  }

  public int getTtl() {
    return ttl;
  }

  public boolean isNullToUnset() {
    return nullToUnset;
  }

  public boolean isDeletesEnabled() {
    return deletesEnabled;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TableConfig other = (TableConfig) o;

    return topicName.equals(other.topicName)
        && keyspace.equals(other.keyspace)
        && table.equals(other.table);
  }

  @Override
  public int hashCode() {
    return Objects.hash(topicName, keyspace, table);
  }

  @Override
  @NotNull
  public String toString() {
    return String.format(
        "{keyspace: %s, table: %s, cl: %s, ttl: %d, nullToUnset: %b, "
            + "deletesEnabled: %b, mapping:\n%s\n"
            + "}",
        keyspace,
        table,
        consistencyLevel,
        ttl,
        nullToUnset,
        deletesEnabled,
        Splitter.on(DELIM_PAT)
            .splitToList(mappingString)
            .stream()
            .map(m -> "      " + m)
            .collect(Collectors.joining("\n")));
  }

  /**
   * Build up a {@link ConfigDef} for the given table specification.
   *
   * @param topicName name of topic
   * @param keyspace name of keyspace
   * @param table name of table
   * @return a ConfigDef of table-settings, where each setting name is the full setting path (e.g.
   *     topic.[topicname].[keyspace].[table].[setting]).
   */
  @NotNull
  private static ConfigDef makeTableConfigDef(
      @NotNull String topicName, @NotNull String keyspace, @NotNull String table) {
    return new ConfigDef()
        .define(
            getTableSettingPath(topicName, keyspace, table, MAPPING_OPT),
            ConfigDef.Type.STRING,
            ConfigDef.Importance.HIGH,
            "Mapping of record fields to dse columns, in the form of 'col1=value.f1, col2=key.f1'")
        .define(
            getTableSettingPath(topicName, keyspace, table, DELETES_ENABLED_OPT),
            ConfigDef.Type.BOOLEAN,
            true,
            ConfigDef.Importance.HIGH,
            "Whether to delete rows where only the primary key is non-null")
        .define(
            getTableSettingPath(topicName, keyspace, table, CL_OPT),
            ConfigDef.Type.STRING,
            "LOCAL_ONE",
            ConfigDef.Importance.HIGH,
            "Query consistency level")
        .define(
            getTableSettingPath(topicName, keyspace, table, TTL_OPT),
            ConfigDef.Type.INT,
            -1,
            ConfigDef.Range.atLeast(-1),
            ConfigDef.Importance.HIGH,
            "TTL of rows inserted in DSE nodes")
        .define(
            getTableSettingPath(topicName, keyspace, table, NULL_TO_UNSET_OPT),
            ConfigDef.Type.BOOLEAN,
            true,
            ConfigDef.Importance.HIGH,
            "Whether nulls in Kafka should be treated as UNSET in DSE");
  }

  @NotNull
  private static CqlIdentifier parseLoosely(@NotNull String value) {
    // If the value is unquoted, treat it as a literal (no real parsing).
    // Otherwise parse it as cql. The idea is that users should be able to specify
    // case-sensitive identifiers in the mapping spec without quotes.

    return value.startsWith("\"")
        ? CqlIdentifier.fromCql(value)
        : CqlIdentifier.fromInternal(value);
  }

  @NotNull
  private Map<CqlIdentifier, CqlIdentifier> parseMappingString(String mappingString) {
    MappingInspector inspector =
        new MappingInspector(
            mappingString,
            getTableSettingPath(topicName, keyspace.asInternal(), table.asInternal(), MAPPING_OPT));
    List<String> errors = inspector.getErrors();
    if (!errors.isEmpty()) {
      throw new ConfigException(
          getTableSettingPath(topicName, keyspace.asInternal(), table.asInternal(), MAPPING_OPT),
          singleQuote(mappingString),
          String.format(
              "Encountered the following errors:%n%s",
              errors.stream().collect(Collectors.joining(String.format("%n  ")))));
    }

    return inspector.getMapping();
  }

  public static class Builder {
    private final String topic;
    private final String keyspace;
    private final String table;
    private final Map<String, String> settings;

    Builder(String topic, String keyspace, String table) {
      this.topic = topic;
      this.keyspace = keyspace;
      this.table = table;
      settings = new HashMap<>();
    }

    void addSetting(String key, String value) {
      settings.put(key, value);
    }

    public TableConfig build() {
      return new TableConfig(topic, keyspace, table, settings);
    }
  }
}