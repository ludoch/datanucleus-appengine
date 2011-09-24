/**********************************************************************
Copyright (c) 2011 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
**********************************************************************/
package com.google.appengine.datanucleus.scostore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.exceptions.NucleusFatalUserException;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.CollectionMetaData;
import org.datanucleus.store.ExecutionContext;
import org.datanucleus.store.FieldValues;
import org.datanucleus.store.ObjectProvider;
import org.datanucleus.store.mapped.DatastoreClass;
import org.datanucleus.store.mapped.mapping.JavaTypeMapping;
import org.datanucleus.store.mapped.mapping.MappingConsumer;
import org.datanucleus.store.types.sco.SCOUtils;
import org.datanucleus.util.ClassUtils;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceConfig;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortPredicate;
import com.google.appengine.datanucleus.DatastoreManager;
import com.google.appengine.datanucleus.DatastoreServiceFactoryInternal;
import com.google.appengine.datanucleus.EntityUtils;
import com.google.appengine.datanucleus.MetaDataUtils;
import com.google.appengine.datanucleus.StorageVersion;
import com.google.appengine.datanucleus.Utils;

/**
 * Abstract base class for backing stores using a "FK" in the element.
 */
public abstract class AbstractFKStore {
  /** Localiser for messages. */
  protected static final Localiser LOCALISER = Localiser.getInstance(
      "org.datanucleus.Localisation", org.datanucleus.ClassConstants.NUCLEUS_CONTEXT_LOADER);

  /** Manager for the GAE datastore. */
  protected DatastoreManager storeMgr;

  /** MetaData for the field/property in the owner with this container. */
  protected AbstractMemberMetaData ownerMemberMetaData;

  protected String elementType;

  /** Metadata for the class of the element. */
  protected AbstractClassMetaData elementCmd;

  /** Metadata for the member of the element (when bidirectional). */
  protected AbstractMemberMetaData elementMemberMetaData;

  protected ClassLoaderResolver clr;

  protected int relationType;

  /** Primary table for the element(s). */
  protected DatastoreClass elementTable;

  /** Mapping for the owner FK column in the element table. */
  protected JavaTypeMapping ownerMapping;

  public AbstractFKStore(AbstractMemberMetaData ownerMmd, DatastoreManager storeMgr, ClassLoaderResolver clr) {
    this.storeMgr = storeMgr;
    this.ownerMemberMetaData = ownerMmd;
    this.clr = clr;
    this.relationType = ownerMemberMetaData.getRelationType(clr);

    CollectionMetaData colmd = ownerMemberMetaData.getCollection();
    if (colmd == null) {
      throw new NucleusUserException(LOCALISER.msg("056001", ownerMemberMetaData.getFullFieldName()));
    }

    // Load the element class
    elementType = colmd.getElementType();
    Class element_class = clr.classForName(elementType);

    if (ClassUtils.isReferenceType(element_class)) {
      if (storeMgr.getNucleusContext().getMetaDataManager().isPersistentInterface(elementType)) {
        elementCmd = storeMgr.getNucleusContext().getMetaDataManager().getMetaDataForInterface(element_class,clr);
      }
      else {
        // Take the metadata for the first implementation of the reference type
        elementCmd = storeMgr.getNucleusContext().getMetaDataManager().getMetaDataForImplementationOfReference(element_class,null,clr);
        if (elementCmd != null)
        {
          // Pretend we have a relationship with this one implementation
          elementType = elementCmd.getFullClassName();
        }
      }
    }
    else {
      // Check that the element class has MetaData
      elementCmd = storeMgr.getNucleusContext().getMetaDataManager().getMetaDataForClass(element_class, clr);
    }
    if (elementCmd == null) {
      throw new NucleusUserException(LOCALISER.msg("056003", element_class.getName(), ownerMemberMetaData.getFullFieldName()));
    }
    elementTable = this.storeMgr.getDatastoreClass(elementCmd.getFullClassName(), clr);

    // Get the field in the element table (if any)
    String mappedByFieldName = ownerMemberMetaData.getMappedBy();
    if (mappedByFieldName != null) {
      // bidirectional - the element class has a field for the owner.
      elementMemberMetaData = elementCmd.getMetaDataForMember(mappedByFieldName);
      if (elementMemberMetaData == null) {
        throw new NucleusUserException(LOCALISER.msg("056024", ownerMemberMetaData.getFullFieldName(), 
            mappedByFieldName, element_class.getName()));
      }

      // Check that the type of the element "mapped-by" field is consistent with the owner type
      if (!clr.isAssignableFrom(elementMemberMetaData.getType(), ownerMmd.getAbstractClassMetaData().getFullClassName())) {
        throw new NucleusUserException(LOCALISER.msg("056025", ownerMmd.getFullFieldName(), 
            elementMemberMetaData.getFullFieldName(), elementMemberMetaData.getTypeName(), ownerMmd.getAbstractClassMetaData().getFullClassName()));
      }
      ownerMapping = elementTable.getMemberMapping(elementMemberMetaData);
    }
    else {
      ownerMapping = elementTable.getExternalMapping(ownerMemberMetaData, MappingConsumer.MAPPING_TYPE_EXTERNAL_FK);
    }
  }

  public DatastoreManager getStoreManager() {
    return storeMgr;
  }

  protected Entity getOwnerEntity(ObjectProvider op) {
    Entity entity = (Entity) op.getAssociatedValue(storeMgr.getDatastoreTransaction(op.getExecutionContext()));
    if (entity == null) {
      storeMgr.validateMetaDataForClass(op.getClassMetaData());
      EntityUtils.getEntityFromDatastore(storeMgr.getDatastoreServiceForReads(op.getExecutionContext()), op, 
          EntityUtils.getPkAsKey(op));
      return (Entity) op.getAssociatedValue(storeMgr.getDatastoreTransaction(op.getExecutionContext()));
    }
    return entity;
  }

  /* (non-Javadoc)
   * @see org.datanucleus.store.scostore.CollectionStore#updateEmbeddedElement(org.datanucleus.store.ObjectProvider, java.lang.Object, int, java.lang.Object)
   */
  public boolean updateEmbeddedElement(ObjectProvider ownerOP, Object elem, int fieldNum, Object value) {
    // This is only used by join table stores where the element is embedded in the join table
    return false;
  }

  /* (non-Javadoc)
   * @see org.datanucleus.store.scostore.CollectionStore#size(org.datanucleus.store.ObjectProvider)
   */
  public int size(ObjectProvider op) {
    Entity ownerEntity = getOwnerEntity(op);

    if (storeMgr.storageVersionAtLeast(StorageVersion.READ_OWNED_CHILD_KEYS_FROM_PARENTS)) {
      // Child keys are stored in field in owner Entity
      String propName = EntityUtils.getPropertyName(storeMgr.getIdentifierFactory(), ownerMemberMetaData);
      if (ownerEntity.hasProperty(propName)) {
        Object value = ownerEntity.getProperty(propName);
        if (value == null) {
          return 0;
        }
        List<Key> keys = (List<Key>) value;
        return keys.size();
      }
      return 0;
    } else if (MetaDataUtils.isOwnedRelation(ownerMemberMetaData)) {
      // Get size from child keys by doing a query with the owner as the parent Entity
      String kindName = elementTable.getIdentifier().getIdentifierName();
      Iterable<Entity> children = prepareChildrenQuery(ownerEntity.getKey(),
          Collections.<FilterPredicate>emptyList(),
          Collections.<SortPredicate>emptyList(), // Sort not important when counting
          true, kindName).asIterable();

      int count = 0;
      for (Entity e : children) {
        if (ownerEntity.getKey().equals(e.getKey().getParent())) {
          count++;
        }
      }
      return count;
    }
    return 0;
  }

  /* (non-Javadoc)
   * @see org.datanucleus.store.scostore.CollectionStore#contains(org.datanucleus.store.ObjectProvider, java.lang.Object)
   */
  public boolean contains(ObjectProvider op, Object element) {
    ExecutionContext ec = op.getExecutionContext();
    if (!validateElementForReading(ec, element)) {
      return false;
    }

    Key childKey = EntityUtils.getKeyForObject(element, ec);
    if (childKey == null) {
      // Not yet persistent
      return false;
    }

    if (storeMgr.storageVersionAtLeast(StorageVersion.READ_OWNED_CHILD_KEYS_FROM_PARENTS)){
      // Check containment using field in parent containing "List<Key>"
      Entity datastoreEntity = getOwnerEntity(op);
      String propName = EntityUtils.getPropertyName(storeMgr.getIdentifierFactory(), ownerMemberMetaData);
      if (datastoreEntity.hasProperty(propName)) {
        Object value = datastoreEntity.getProperty(propName);
        if (value == null) {
          return false;
        } else {
          List<Key> keys = (List<Key>)value;
          return keys.contains(childKey);
        }
      } else {
        return false;
      }
    } else if (MetaDataUtils.isOwnedRelation(ownerMemberMetaData)) {
      // Check containment using parent key of the element key
      // Child key can be null if element has not yet been persisted
      if (childKey.getParent() == null) {
        return false;
      }
      Key parentKey = EntityUtils.getPrimaryKeyAsKey(ec.getApiAdapter(), op);
      return childKey.getParent().equals(parentKey);
    }
    return false;
  }

  /**
   * Method to run a query with the supplied filter and sort predicates, to get the child objects for the 
   * specified parent.
   * @param parentKey Key of the parent
   * @param filterPredicates Filtering required
   * @param sortPredicates Ordering required
   * @param ec ExecutionContext
   * @return The child objects list
   */
  List<?> getChildrenUsingParentQuery(Key parentKey, Iterable<FilterPredicate> filterPredicates,
      Iterable<SortPredicate> sortPredicates, ExecutionContext ec) {
    List<Object> result = new ArrayList<Object>();
    int numChildren = 0;
    String kindName = elementTable.getIdentifier().getIdentifierName();
    for (Entity e : prepareChildrenQuery(parentKey, filterPredicates, sortPredicates, false, kindName).asIterable()) {
      // We only want direct children
      if (parentKey.equals(e.getKey().getParent())) {
        numChildren++;
        result.add(EntityUtils.entityToPojo(e, elementCmd, clr, ec, false, ec.getFetchPlan()));
        if (NucleusLogger.PERSISTENCE.isDebugEnabled()) {
          NucleusLogger.PERSISTENCE.debug("Retrieved entity with key " + e.getKey());
        }
      }
    }
    NucleusLogger.PERSISTENCE.debug(String.format("Query had %d result%s.", numChildren, numChildren == 1 ? "" : "s"));
    return result;
  }

  /**
   * Method to return the List of children for this collection using the "List<Key>" stored in the owner field.
   * @param op ObjectProvider for the owner
   * @param ec ExecutionContext
   * @return The child objects list
   */
  List<?> getChildrenFromParentField(ObjectProvider op, ExecutionContext ec) {
    Entity datastoreEntity = getOwnerEntity(op);
    String propName = EntityUtils.getPropertyName(storeMgr.getIdentifierFactory(), ownerMemberMetaData);
    if (datastoreEntity.hasProperty(propName)) {
      Object value = datastoreEntity.getProperty(propName);
      if (value == null) {
        return Utils.newArrayList();
      }

      List children = new ArrayList();
      List<Key> keys = (List<Key>)value;
      DatastoreServiceConfig config = storeMgr.getDefaultDatastoreServiceConfigForReads();
      DatastoreService ds = DatastoreServiceFactoryInternal.getDatastoreService(config);
      Map<Key, Entity> entitiesByKey = ds.get(keys);
      for (Key key : keys) {
        Entity entity = entitiesByKey.get(key);
        if (entity == null) {
          throw new NucleusFatalUserException("Field in parent with key=" + datastoreEntity.getKey() + 
              " refers to child of " + key + " but this doesn't exist! Check your data integrity");
        }
        Object pojo = EntityUtils.entityToPojo(entity, elementCmd, clr, ec, false, ec.getFetchPlan());
        children.add(pojo);
      }
      return children;
    }
    return Utils.newArrayList();
  }

  /**
   * Method to create a PreparedQuery, for the specified filter and ordering, to get the child objects of a parent.
   * @param parentKey Key of the parent
   * @param filterPredicates Filtering required
   * @param sortPredicates Ordering required
   * @param keysOnly Whether to just returns the keys of the children
   * @param kindName Name of the kind that we are querying
   * @return The PreparedQuery
   */
  PreparedQuery prepareChildrenQuery(Key parentKey, Iterable<FilterPredicate> filterPredicates,
      Iterable<SortPredicate> sortPredicates, boolean keysOnly, String kindName) {
    Query q = new Query(kindName, parentKey);
    if (keysOnly) {
      q.setKeysOnly();
    }

    NucleusLogger.PERSISTENCE.debug("Preparing to query for all children of " + parentKey + " of kind " + kindName);
    for (FilterPredicate fp : filterPredicates) {
      q.addFilter(fp.getPropertyName(), fp.getOperator(), fp.getValue());
      NucleusLogger.PERSISTENCE.debug("  Added filter: " + fp.getPropertyName() + " " + fp.getOperator() + " " + fp.getValue());
    }
    for (SortPredicate sp : sortPredicates) {
      q.addSort(sp.getPropertyName(), sp.getDirection());
      NucleusLogger.PERSISTENCE.debug("  Added sort: " + sp.getPropertyName() + " " + sp.getDirection());
    }

    DatastoreServiceConfig config = storeMgr.getDefaultDatastoreServiceConfigForReads();
    DatastoreService ds = DatastoreServiceFactoryInternal.getDatastoreService(config);
    return ds.prepare(q);
  }

  /**
   * Method to check if an element is already persistent, or is managed by a different
   * persistence manager. If not persistent, this will persist it.
   * @param ec ExecutionContext
   * @param element The element
   * @param fieldValues any initial field values to use if persisting the element
   * @return Whether the element was persisted during this call
   */
  protected boolean validateElementForWriting(ExecutionContext ec, Object element, FieldValues fieldValues) {
    // Check the element type for this collection
    if (!storeMgr.getNucleusContext().getMetaDataManager().isPersistentInterface(elementType) &&
        !validateElementType(ec.getClassLoaderResolver(), element)) {
      throw new ClassCastException(LOCALISER.msg("056033", element.getClass().getName(), 
          ownerMemberMetaData.getFullFieldName(), elementType));
    }

    return SCOUtils.validateObjectForWriting(ec, element, fieldValues);
  }

  /**
   * Method to check if an element is already persistent or is persistent but managed by 
   * a different persistence manager.
   * @param ec ExecutionContext
   * @param element The element
   * @return Whether it is valid for reading.
   */
  protected boolean validateElementForReading(ExecutionContext ec, Object element) {
    if (!validateElementType(clr, element)) {
      return false;
    }

    if (element != null) {
      if ((!ec.getApiAdapter().isPersistent(element) || ec != ec.getApiAdapter().getExecutionContext(element)) && 
          !ec.getApiAdapter().isDetached(element)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Method to validate an element against the accepted type.
   * @param clr The ClassLoaderResolver
   * @param element The element to validate
   * @return Whether it is valid.
   */ 
  protected boolean validateElementType(ClassLoaderResolver clr, Object element) {
    if (element == null) {
      return true;
    }

    Class primitiveElementClass = ClassUtils.getPrimitiveTypeForType(element.getClass());
    if (primitiveElementClass != null) {
      // Allow for the element type being primitive, and the user wanting to store its wrapper
      String elementTypeWrapper = elementType;
      Class elementTypeClass = clr.classForName(elementType);
      if (elementTypeClass.isPrimitive()) {
        elementTypeWrapper = ClassUtils.getWrapperTypeForPrimitiveType(elementTypeClass).getName();
      }
      return clr.isAssignableFrom(elementTypeWrapper, element.getClass());
    }
    return clr.isAssignableFrom(elementType, element.getClass());
  }
}