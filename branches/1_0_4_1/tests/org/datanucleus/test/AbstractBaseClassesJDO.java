/*
 * Copyright (C) 2009 Max Ross.
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
package org.datanucleus.test;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.Inheritance;
import javax.jdo.annotations.InheritanceStrategy;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

/**
 * @author Max Ross <max.ross@gmail.com>
 */
public class AbstractBaseClassesJDO {

  private AbstractBaseClassesJDO() {}

  @PersistenceCapable(identityType = IdentityType.APPLICATION)
  @Inheritance(strategy = InheritanceStrategy.SUBCLASS_TABLE)
  public abstract static class Base1 {
    @PrimaryKey
    @Persistent(valueStrategy = IdGeneratorStrategy.IDENTITY)
    private Long id;

    private String base1Str;

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getBase1Str() {
      return base1Str;
    }

    public void setBase1Str(String base1Str) {
      this.base1Str = base1Str;
    }
  }

  @PersistenceCapable(identityType = IdentityType.APPLICATION)
  public static class Concrete1 extends Base1 {
    private String concrete1Str;

    public String getConcrete1Str() {
      return concrete1Str;
    }

    public void setConcrete1Str(String concrete1Str) {
      this.concrete1Str = concrete1Str;
    }
  }

  @PersistenceCapable(identityType = IdentityType.APPLICATION)
  @Inheritance(strategy = InheritanceStrategy.SUBCLASS_TABLE)
  public abstract static class Base2 extends Base1 {
    private String base2Str;

    public String getBase2Str() {
      return base2Str;
    }

    public void setBase2Str(String base2Str) {
      this.base2Str = base2Str;
    }
  }

  @PersistenceCapable(identityType = IdentityType.APPLICATION)
  public static class Concrete2 extends Base2 {
    private String concrete2Str;

    public String getConcrete2Str() {
      return concrete2Str;
    }

    public void setConcrete2Str(String concrete2Str) {
      this.concrete2Str = concrete2Str;
    }
  }
}
