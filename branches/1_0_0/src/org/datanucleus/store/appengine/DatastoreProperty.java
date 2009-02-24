// Copyright 2008 Google Inc. All Rights Reserved.
package org.datanucleus.store.appengine;

import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.ColumnMetaData;
import org.datanucleus.store.mapped.DatastoreField;
import org.datanucleus.store.mapped.DatastoreIdentifier;
import org.datanucleus.store.mapped.MappedStoreManager;
import org.datanucleus.store.mapped.mapping.DatastoreMapping;
import org.datanucleus.store.mapped.mapping.JavaTypeMapping;

/**
 * Describes a property in the datastore.
 *
 * Mostly copied from Column.
 *
 * @author Max Ross <maxr@google.com>
 */
class DatastoreProperty implements DatastoreField {

  /** Identifier for the column in the datastore. */
  private DatastoreIdentifier identifier;

  /** ColumnMetaData for this column. */
  private ColumnMetaData columnMetaData;

  /** Table containing this column in the datastore. */
  private final DatastoreTable table;

  /** Datastore mapping for this column. */
  private DatastoreMapping datastoreMapping = null;

  /** Java type that this column is storing. (can we just get this from the mapping above ?) */
  private final String storedJavaType;

  /** Manager for the store into which we are persisting. */
  private final MappedStoreManager storeMgr;

  /** Flag indicated whether or not this is a pk */
  private boolean isPrimaryKey;

  /**
   * {@link #getMemberMetaData()} typically derives this from the parent of
   * {@link #columnMetaData} but if this member is set it just returns
   * it directly.
   */
  private AbstractMemberMetaData ammd;

  public DatastoreProperty(DatastoreTable table, String javaType,
      DatastoreIdentifier identifier, ColumnMetaData colmd) {
    this.table = table;
    this.storedJavaType = javaType;
    this.storeMgr = table.getStoreManager();

    setIdentifier(identifier);
    if (colmd == null) {
      // Create a default ColumnMetaData since none provided
      columnMetaData = new ColumnMetaData();
    } else {
      columnMetaData = colmd;
    }

    // Uniqueness
    if (columnMetaData.getUnique()) {
      // MetaData requires it to be unique
      throw new UnsupportedOperationException("No support for uniqueness constraints");
    }
  }

  public String getStoredJavaType() {
    return storedJavaType;
  }

  public void setAsPrimaryKey() {
    isPrimaryKey = true;
  }

  public boolean isPrimaryKey() {
    return isPrimaryKey;
  }

  public boolean isNullable() {
    // all properties all nullable
    return true;
  }

  public DatastoreMapping getDatastoreMapping() {
    return datastoreMapping;
  }

  public void setDatastoreMapping(DatastoreMapping mapping) {
    this.datastoreMapping = mapping;
  }

  public DatastoreTable getDatastoreContainerObject() {
    return table;
  }

  public String applySelectFunction(String replacementValue) {
    throw new UnsupportedOperationException("Select function not supported");
  }

  public void copyConfigurationTo(DatastoreField field) {
    DatastoreProperty prop = (DatastoreProperty) field;
    prop.isPrimaryKey = this.isPrimaryKey;
  }

  public DatastoreField setNullable() {
    // all properties are nullable
    return this;
  }

  public DatastoreField setDefaultable() {
    throw new UnsupportedOperationException("Default values not supported");
  }

  public void setIdentifier(DatastoreIdentifier identifier) {
    this.identifier = identifier;
  }

  public MappedStoreManager getStoreManager() {
    return storeMgr;
  }

  public DatastoreIdentifier getIdentifier() {
    return identifier;
  }

  public boolean isDefaultable() {
    return false;
  }

  public DatastoreField setUnique() {
    return this;
  }

  public boolean isUnique() {
    return false;
  }

  public DatastoreField setIdentity(boolean b) {
    isPrimaryKey = b;
    return this;
  }

  public boolean isIdentity() {
    return isPrimaryKey();
  }

  public void setDefaultValue(Object o) {
    throw new UnsupportedOperationException("Default values not supported.");
  }

  public Object getDefaultValue() {
    return null;
  }

  public void setColumnMetaData(ColumnMetaData columnMetaData) {
    this.columnMetaData = columnMetaData;
  }

  public ColumnMetaData getColumnMetaData() {
    return columnMetaData;
  }

  public JavaTypeMapping getJavaTypeMapping() {
    return datastoreMapping.getJavaTypeMapping();
  }

  public AbstractMemberMetaData getMemberMetaData() {
    if (ammd != null) {
      return ammd;
    }
    if (columnMetaData != null && columnMetaData.getParent() instanceof AbstractMemberMetaData) {
        return (AbstractMemberMetaData)columnMetaData.getParent();
    }
    return null;
  }

  void setMemberMetaData(AbstractMemberMetaData ammd) {
    this.ammd = ammd;
  }
}
