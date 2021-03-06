/**********************************************************************
Copyright (c) 2008 Andy Jefferson and others. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Contributors:
    ...
**********************************************************************/
package org.datanucleus.store.mapped.mapping;

import org.datanucleus.metadata.DiscriminatorMetaData;
import org.datanucleus.store.mapped.DatastoreAdapter;
import org.datanucleus.store.mapped.DatastoreContainerObject;

/**
 * Discriminator using a Long delegate.
 */
public final class DiscriminatorLongMapping extends DiscriminatorMapping
{
    /**
     * Constructor.
     * @param dba Datastore Adapter
     * @param table Datastore table
     * @param delegate The JavaTypeMapping to delegate storage
     */
    public DiscriminatorLongMapping(DatastoreAdapter dba, DatastoreContainerObject table, JavaTypeMapping delegate)
    {
        super(dba, table, delegate, table.getDiscriminatorMetaData());
    }

    /**
     * Constructor.
     * @param dba Datastore Adapter
     * @param table Datastore table
     * @param delegate The JavaTypeMapping to delegate storage
     * @param dismd Discriminator metadata
     */
    public DiscriminatorLongMapping(DatastoreAdapter dba, DatastoreContainerObject table, JavaTypeMapping delegate,
            DiscriminatorMetaData dismd)
    {
        super(dba, table, delegate, dismd);
    }
}