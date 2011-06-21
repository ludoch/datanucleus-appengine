/**********************************************************************
Copyright (c) 2009 Google Inc.

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
package com.google.appengine.datanucleus.appengine.jpa;

import org.datanucleus.jpa.exceptions.NoPersistenceUnitException;
import org.datanucleus.jpa.exceptions.NoPersistenceXmlException;
import org.datanucleus.jpa.exceptions.NotProviderException;

import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;

/**
 * @author Max Ross <maxr@google.com>
 */
public class DatastorePersistenceProvider implements PersistenceProvider {

  public EntityManagerFactory createEntityManagerFactory(String unitInfo, Map properties) {
    try {
      return new DatastoreEntityManagerFactory(unitInfo, properties);
    } catch (NotProviderException npe) {
    }
    // spec says to not let exceptions propagate, just return null
    return null;
  }

  public EntityManagerFactory createContainerEntityManagerFactory(
      PersistenceUnitInfo unitInfo, Map properties) {
    try {
      return new DatastoreEntityManagerFactory(unitInfo, properties);
    } catch (NotProviderException npe) {
    } catch (NoPersistenceUnitException npue) {
    } catch (NoPersistenceXmlException npxe) {
    }
    // spec says to not let exceptions propagate, just return null
    return null;
  }
}
