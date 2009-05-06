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
package org.datanucleus.store.appengine.query;

import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Query.SortPredicate;
import com.google.appengine.api.datastore.ShortBlob;
import com.google.appengine.repackaged.com.google.common.collect.PrimitiveArrays;
import com.google.apphosting.api.ApiProxy;

import org.datanucleus.ObjectManager;
import org.datanucleus.exceptions.NucleusDataStoreException;
import org.datanucleus.jpa.EntityManagerImpl;
import org.datanucleus.jpa.JPAQuery;
import org.datanucleus.query.expression.Expression;
import org.datanucleus.store.appengine.ExceptionThrowingDatastoreDelegate;
import org.datanucleus.store.appengine.JPATestCase;
import org.datanucleus.store.appengine.TestUtils;
import org.datanucleus.store.appengine.Utils;
import org.datanucleus.test.BidirectionalChildListJPA;
import org.datanucleus.test.Book;
import org.datanucleus.test.Flight;
import org.datanucleus.test.HasBytesJPA;
import org.datanucleus.test.HasDoubleJPA;
import org.datanucleus.test.HasEnumJPA;
import org.datanucleus.test.HasKeyPkJPA;
import org.datanucleus.test.HasLongPkJPA;
import org.datanucleus.test.HasMultiValuePropsJPA;
import org.datanucleus.test.HasOneToManyKeyPkListJPA;
import org.datanucleus.test.HasOneToManyKeyPkSetJPA;
import org.datanucleus.test.HasOneToManyListJPA;
import org.datanucleus.test.HasOneToManyLongPkListJPA;
import org.datanucleus.test.HasOneToManyLongPkSetJPA;
import org.datanucleus.test.HasOneToManyUnencodedStringPkListJPA;
import org.datanucleus.test.HasOneToManyUnencodedStringPkSetJPA;
import org.datanucleus.test.HasOneToOneJPA;
import org.datanucleus.test.HasOneToOneParentJPA;
import org.datanucleus.test.HasStringAncestorStringPkJPA;
import org.datanucleus.test.HasUnencodedStringPkJPA;
import org.datanucleus.test.KitchenSink;
import org.datanucleus.test.NullDataJPA;
import org.datanucleus.test.Person;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

public class JPQLQueryTest extends JPATestCase {

  private static final List<SortPredicate> NO_SORTS = Collections.emptyList();
  private static final List<FilterPredicate> NO_FILTERS = Collections.emptyList();

  private static final FilterPredicate TITLE_EQ_2 =
      new FilterPredicate("title", FilterOperator.EQUAL, 2L);
  private static final FilterPredicate TITLE_EQ_2STR =
      new FilterPredicate("title", FilterOperator.EQUAL, "2");
  private static final FilterPredicate ISBN_EQ_4 =
      new FilterPredicate("isbn", FilterOperator.EQUAL, 4L);
  private static final FilterPredicate TITLE_GT_2 =
      new FilterPredicate("title", FilterOperator.GREATER_THAN, 2L);
  private static final FilterPredicate TITLE_GTE_2 =
      new FilterPredicate("title", FilterOperator.GREATER_THAN_OR_EQUAL, 2L);
  private static final FilterPredicate ISBN_LT_4 =
      new FilterPredicate("isbn", FilterOperator.LESS_THAN, 4L);
  private static final FilterPredicate ISBN_LTE_4 =
      new FilterPredicate("isbn", FilterOperator.LESS_THAN_OR_EQUAL, 4L);
  private static final
  SortPredicate
      TITLE_ASC =
      new SortPredicate("title", SortDirection.ASCENDING);
  private static final
  SortPredicate
      ISBN_DESC =
      new SortPredicate("isbn", SortDirection.DESCENDING);

  @Override
  protected EntityManagerFactoryName getEntityManagerFactoryName() {
    return EntityManagerFactoryName.nontransactional_ds_non_transactional_ops_allowed;
  }

  public void testUnsupportedFilters_NoResultExpr() {
    String baseQuery = "SELECT FROM " + Book.class.getName() + " ";
    testUnsupportedFilters(baseQuery);
  }

  public void testUnsupportedFilters_PrimaryResultExpr() {
    String baseQuery = "SELECT b FROM " + Book.class.getName() + " b ";
    testUnsupportedFilters(baseQuery);
  }

  private void testUnsupportedFilters(String baseQuery) {
    assertQueryUnsupportedByOrm(baseQuery + "GROUP BY author", DatastoreQuery.GROUP_BY_OP);
    // Can't actually test having because the parser doesn't recognize it unless there is a
    // group by, and the group by gets seen first.
    assertQueryUnsupportedByOrm(baseQuery + "GROUP BY author HAVING title = 'foo'",
                                DatastoreQuery.GROUP_BY_OP);
    assertQueryUnsupportedByOrm(
        "select avg(firstPublished) from " + Book.class.getName(),
        new Expression.Operator("avg", 0));

    Set<Expression.Operator> unsupportedOps =
        new HashSet<Expression.Operator>(DatastoreQuery.UNSUPPORTED_OPERATORS);
    baseQuery += "WHERE ";
    assertQueryUnsupportedByOrm(baseQuery + "title = 'foo' OR title = 'bar'", Expression.OP_OR,
                                unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "NOT title = 'foo'", Expression.OP_NOT, unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "(title + author) = 'foo'", Expression.OP_ADD,
                                unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title + author = 'foo'", Expression.OP_ADD,
                                unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "(title - author) = 'foo'", Expression.OP_SUB,
                                unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title - author = 'foo'", Expression.OP_SUB,
                                unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "(title / author) = 'foo'", Expression.OP_DIV,
                                unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title / author = 'foo'", Expression.OP_DIV,
                                unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "(title * author) = 'foo'", Expression.OP_MUL,
                                unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title * author = 'foo'", Expression.OP_MUL,
                                unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "(title % author) = 'foo'", Expression.OP_MOD,
                                unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title % author = 'foo'", Expression.OP_MOD,
                                unsupportedOps);
    assertQueryUnsupportedByOrm(baseQuery + "title LIKE 'foo%'", Expression.OP_LIKE,
                                unsupportedOps);
    // multiple inequality filters
    // TODO(maxr) Make this pass against the real datastore.
    // We need to have it return BadRequest instead of NeedIndex for that to
    // happen.
    assertQueryUnsupportedByDatastore(baseQuery + "(title > 2 AND isbn < 4)");
    // inequality filter prop is not the same as the first order by prop
    assertQueryUnsupportedByDatastore(baseQuery + "(title > 2) order by isbn");

    assertEquals(
        new HashSet<Expression.Operator>(Arrays.asList(Expression.OP_CONCAT, Expression.OP_COM,
                                                       Expression.OP_NEG, Expression.OP_IS,
                                                       Expression.OP_BETWEEN,
                                                       Expression.OP_ISNOT)), unsupportedOps);
  }

  public void testSupportedFilters_NoResultExpr() {
    String baseQuery = "SELECT FROM " + Book.class.getName() + " ";
    testSupportedFilters(baseQuery);
  }

  public void testSupportedFilters_PrimaryResultExpr() {
    String baseQuery = "SELECT b FROM " + Book.class.getName() + " b ";
    testSupportedFilters(baseQuery);
  }

  private void testSupportedFilters(String baseQuery) {

    assertQuerySupported(baseQuery, NO_FILTERS, NO_SORTS);

    baseQuery += "WHERE ";
    assertQuerySupported(baseQuery + "title = 2", Utils.newArrayList(TITLE_EQ_2), NO_SORTS);
    assertQuerySupported(baseQuery + "title = \"2\"", Utils.newArrayList(TITLE_EQ_2STR), NO_SORTS);
    assertQuerySupported(baseQuery + "(title = 2)", Utils.newArrayList(TITLE_EQ_2), NO_SORTS);
    assertQuerySupported(baseQuery + "title = 2 AND isbn = 4", Utils.newArrayList(TITLE_EQ_2,
                                                                                  ISBN_EQ_4),
                         NO_SORTS);
    assertQuerySupported(baseQuery + "(title = 2 AND isbn = 4)", Utils.newArrayList(TITLE_EQ_2,
                                                                                    ISBN_EQ_4),
                         NO_SORTS);
    assertQuerySupported(baseQuery + "(title = 2) AND (isbn = 4)", Utils.newArrayList(
        TITLE_EQ_2, ISBN_EQ_4), NO_SORTS);
    assertQuerySupported(baseQuery + "title > 2", Utils.newArrayList(TITLE_GT_2), NO_SORTS);
    assertQuerySupported(baseQuery + "title >= 2", Utils.newArrayList(TITLE_GTE_2), NO_SORTS);
    assertQuerySupported(baseQuery + "isbn < 4", Utils.newArrayList(ISBN_LT_4), NO_SORTS);
    assertQuerySupported(baseQuery + "isbn <= 4", Utils.newArrayList(ISBN_LTE_4), NO_SORTS);

    baseQuery = "SELECT FROM " + Book.class.getName() + " ";
    assertQuerySupported(baseQuery + "ORDER BY title ASC", NO_FILTERS,
                         Utils.newArrayList(TITLE_ASC));
    assertQuerySupported(baseQuery + "ORDER BY isbn DESC", NO_FILTERS,
                         Utils.newArrayList(ISBN_DESC));
    assertQuerySupported(baseQuery + "ORDER BY title ASC, isbn DESC", NO_FILTERS,
                         Utils.newArrayList(TITLE_ASC, ISBN_DESC));

    assertQuerySupported(baseQuery + "WHERE title = 2 AND isbn = 4 ORDER BY title ASC, isbn DESC",
                         Utils.newArrayList(TITLE_EQ_2, ISBN_EQ_4),
                         Utils.newArrayList(TITLE_ASC, ISBN_DESC));
  }

  public void test2Equals2OrderBy() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890"));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111"));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345"));
    ldth.ds.put(newBook("A Book", "Joe Blow", "54321"));
    ldth.ds.put(newBook("Baz Book", "Jane Blow", "13579"));

    Query q = em.createQuery("SELECT FROM " +
                             Book.class.getName() +
                             " WHERE author = 'Joe Blow'" +
                             " ORDER BY title DESC, isbn ASC");

    @SuppressWarnings("unchecked")
    List<Book> result = (List<Book>) q.getResultList();

    assertEquals(4, result.size());
    assertEquals("12345", result.get(0).getIsbn());
    assertEquals("11111", result.get(1).getIsbn());
    assertEquals("67890", result.get(2).getIsbn());
    assertEquals("54321", result.get(3).getIsbn());
  }

  public void testDefaultOrderingIsAsc() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890"));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111"));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345"));
    ldth.ds.put(newBook("A Book", "Joe Blow", "54321"));
    ldth.ds.put(newBook("Baz Book", "Jane Blow", "13579"));

    Query q = em.createQuery("SELECT FROM " +
                             Book.class.getName() +
                             " WHERE author = 'Joe Blow'" +
                             " ORDER BY title");

    @SuppressWarnings("unchecked")
    List<Book> result = (List<Book>) q.getResultList();

    assertEquals(4, result.size());
    assertEquals("54321", result.get(0).getIsbn());
    assertEquals("67890", result.get(1).getIsbn());
    assertEquals("11111", result.get(2).getIsbn());
    assertEquals("12345", result.get(3).getIsbn());
  }

  public void testLimitQuery() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890"));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111"));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345"));
    ldth.ds.put(newBook("A Book", "Joe Blow", "54321"));
    ldth.ds.put(newBook("Baz Book", "Jane Blow", "13579"));

    Query q = em.createQuery("SELECT FROM " +
                             Book.class.getName() +
                             " WHERE author = 'Joe Blow'" +
                             " ORDER BY title DESC, isbn ASC");

    q.setMaxResults(1);
    @SuppressWarnings("unchecked")
    List<Book> result1 = (List<Book>) q.getResultList();
    assertEquals(1, result1.size());
    assertEquals("12345", result1.get(0).getIsbn());

    q.setMaxResults(0);
    @SuppressWarnings("unchecked")
    List<Book> result2 = (List<Book>) q.getResultList();
    assertEquals(0, result2.size());

    try {
      q.setMaxResults(-1);
      fail("expected iae");
    } catch (IllegalArgumentException iae) {
      // good
    }
  }

  public void testOffsetQuery() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890"));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111"));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345"));
    ldth.ds.put(newBook("A Book", "Joe Blow", "54321"));
    ldth.ds.put(newBook("Baz Book", "Jane Blow", "13579"));
    Query q = em.createQuery("SELECT FROM " +
                             Book.class.getName() +
                             " WHERE author = 'Joe Blow'" +
                             " ORDER BY title DESC, isbn ASC");

    q.setFirstResult(0);
    @SuppressWarnings("unchecked")
    List<Book> result1 = (List<Book>) q.getResultList();
    assertEquals(4, result1.size());
    assertEquals("12345", result1.get(0).getIsbn());

    q.setFirstResult(1);
    @SuppressWarnings("unchecked")
    List<Book> result2 = (List<Book>) q.getResultList();
    assertEquals(3, result2.size());
    assertEquals("11111", result2.get(0).getIsbn());

    try {
      q.setFirstResult(-1);
      fail("expected iae");
    } catch (IllegalArgumentException iae) {
      // good
    }
  }

  public void testOffsetLimitQuery() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890"));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111"));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345"));
    ldth.ds.put(newBook("A Book", "Joe Blow", "54321"));
    ldth.ds.put(newBook("Baz Book", "Jane Blow", "13579"));
    Query q = em.createQuery("SELECT FROM " +
                             Book.class.getName() +
                             " WHERE author = 'Joe Blow'" +
                             " ORDER BY title DESC, isbn ASC");

    q.setFirstResult(0);
    q.setMaxResults(0);
    @SuppressWarnings("unchecked")
    List<Book> result1 = (List<Book>) q.getResultList();
    assertEquals(0, result1.size());

    q.setFirstResult(1);
    q.setMaxResults(0);
    @SuppressWarnings("unchecked")
    List<Book> result2 = (List<Book>) q.getResultList();
    assertEquals(0, result2.size());

    q.setFirstResult(0);
    q.setMaxResults(1);
    @SuppressWarnings("unchecked")
    List<Book> result3 = (List<Book>) q.getResultList();
    assertEquals(1, result3.size());

    q.setFirstResult(0);
    q.setMaxResults(2);
    @SuppressWarnings("unchecked")
    List<Book> result4 = (List<Book>) q.getResultList();
    assertEquals(2, result4.size());
    assertEquals("12345", result4.get(0).getIsbn());

    q.setFirstResult(1);
    q.setMaxResults(1);
    @SuppressWarnings("unchecked")
    List<Book> result5 = (List<Book>) q.getResultList();
    assertEquals(1, result5.size());
    assertEquals("11111", result5.get(0).getIsbn());

    q.setFirstResult(2);
    q.setMaxResults(5);
    @SuppressWarnings("unchecked")
    List<Book> result6 = (List<Book>) q.getResultList();
    assertEquals(2, result6.size());
    assertEquals("67890", result6.get(0).getIsbn());
  }

  public void testSerialization() throws IOException {
    Query q = em.createQuery("select from " + Book.class.getName());
    q.getResultList();

    JPQLQuery innerQuery = (JPQLQuery) ((JPAQuery) q).getInternalQuery();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    // the fact that this doesn't blow up is the test
    oos.writeObject(innerQuery);
  }

  public void testBindVariables() {

    assertQuerySupported("select from " + Book.class.getName() + " where title = :title",
                         Utils.newArrayList(TITLE_EQ_2), NO_SORTS, "title", 2L);

    assertQuerySupported("select from " + Book.class.getName()
                         + " where title = :title AND isbn = :isbn",
                         Utils.newArrayList(TITLE_EQ_2, ISBN_EQ_4), NO_SORTS, "title", 2L, "isbn",
                         4L);

    assertQuerySupported("select from " + Book.class.getName()
                         + " where title = :title AND isbn = :isbn order by title asc, isbn desc",
                         Utils.newArrayList(TITLE_EQ_2, ISBN_EQ_4),
                         Utils.newArrayList(TITLE_ASC, ISBN_DESC), "title", 2L, "isbn", 4L);
  }

  public void testKeyQuery() {
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName() + " where id = :key");
    q.setParameter("key", KeyFactory.keyToString(bookEntity.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(1, books.size());
    assertEquals(bookEntity.getKey(), KeyFactory.stringToKey(books.get(0).getId()));

    // now issue the same query, but instead of providing a String version of
    // the key, provide the Key itself.
    q.setParameter("key", bookEntity.getKey());
    @SuppressWarnings("unchecked")
    List<Book> books2 = (List<Book>) q.getResultList();
    assertEquals(1, books2.size());
    assertEquals(bookEntity.getKey(), KeyFactory.stringToKey(books2.get(0).getId()));
  }

  public void testKeyQuery_KeyPk() {
    Entity e = new Entity(HasKeyPkJPA.class.getSimpleName());
    ldth.ds.put(e);

    javax.persistence.Query q = em.createQuery(
        "select from " + HasKeyPkJPA.class.getName() + " where id = :key");
    q.setParameter("key", e.getKey());
    @SuppressWarnings("unchecked")
    List<HasKeyPkJPA> result = (List<HasKeyPkJPA>) q.getResultList();
    assertEquals(1, result.size());
    assertEquals(e.getKey(), result.get(0).getId());
  }

  public void testKeyQueryWithSorts() {
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName()
        + " where id = :key order by isbn ASC");
    q.setParameter("key", KeyFactory.keyToString(bookEntity.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(1, books.size());
    assertEquals(bookEntity.getKey(), KeyFactory.stringToKey(books.get(0).getId()));
  }

  public void testKeyQuery_MultipleFilters() {
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName()
        + " where id = :key and isbn = \"67890\"");
    q.setParameter("key", KeyFactory.keyToString(bookEntity.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(1, books.size());
    assertEquals(bookEntity.getKey(), KeyFactory.stringToKey(books.get(0).getId()));
  }

  public void testKeyQuery_NonEqualityFilter() {
    Entity bookEntity1 = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity1);

    Entity bookEntity2 = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity2);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName()
        + " where id > :key");
    q.setParameter("key", KeyFactory.keyToString(bookEntity1.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(1, books.size());
    assertEquals(bookEntity2.getKey(), KeyFactory.stringToKey(books.get(0).getId()));
  }

  public void testKeyQuery_SortByKey() {
    Entity bookEntity1 = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity1);

    Entity bookEntity2 = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity2);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName()
        + " order by id DESC");
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(2, books.size());
    assertEquals(bookEntity2.getKey(), KeyFactory.stringToKey(books.get(0).getId()));
  }

  public void testAncestorQuery() {
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);
    Entity
        hasAncestorEntity =
        new Entity(HasStringAncestorStringPkJPA.class.getSimpleName(), bookEntity.getKey());
    ldth.ds.put(hasAncestorEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + HasStringAncestorStringPkJPA.class.getName()
        + " where ancestorId = :ancId");
    q.setParameter("ancId", KeyFactory.keyToString(bookEntity.getKey()));

    @SuppressWarnings("unchecked")
    List<HasStringAncestorStringPkJPA>
        haList =
        (List<HasStringAncestorStringPkJPA>) q.getResultList();
    assertEquals(1, haList.size());
    assertEquals(bookEntity.getKey(), KeyFactory.stringToKey(haList.get(0).getAncestorId()));

    assertEquals(
        bookEntity.getKey(), getDatastoreQuery(q).getLatestDatastoreQuery().getAncestor());
    assertEquals(NO_FILTERS, getFilterPredicates(q));
    assertEquals(NO_SORTS, getSortPredicates(q));
  }

  public void testIllegalAncestorQuery() {
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);
    Entity
        hasAncestorEntity =
        new Entity(HasStringAncestorStringPkJPA.class.getName(), bookEntity.getKey());
    ldth.ds.put(hasAncestorEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + HasStringAncestorStringPkJPA.class.getName()
        + " where ancestorId > :ancId");
    q.setParameter("ancId", KeyFactory.keyToString(bookEntity.getKey()));
    try {
      q.getResultList();
      fail("expected udfe");
    } catch (DatastoreQuery.UnsupportedDatastoreFeatureException udfe) {
      // good
    }
  }

  public void testSortByFieldWithCustomColumn() {
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "67890", 2003));
    ldth.ds.put(newBook("Bar Book", "Joe Blow", "11111", 2002));
    ldth.ds.put(newBook("Foo Book", "Joe Blow", "12345", 2001));

    Query q = em.createQuery("SELECT FROM " +
                             Book.class.getName() +
                             " WHERE author = 'Joe Blow'" +
                             " ORDER BY firstPublished ASC");

    @SuppressWarnings("unchecked")
    List<Book> result = (List<Book>) q.getResultList();

    assertEquals(3, result.size());
    assertEquals("12345", result.get(0).getIsbn());
    assertEquals("11111", result.get(1).getIsbn());
    assertEquals("67890", result.get(2).getIsbn());
  }

  private interface BookProvider {

    Book getBook(Key key);
  }

  private class AttachedBookProvider implements BookProvider {

    public Book getBook(Key key) {
      return em.find(Book.class, key);
    }
  }

  private class TransientBookProvider implements BookProvider {

    public Book getBook(Key key) {
      Book b = new Book();
      b.setId(KeyFactory.keyToString(key));
      return b;
    }
  }

  private void testFilterByChildObject(BookProvider bp) {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bookEntity = newBookEntity(parentEntity.getKey(), "Bar Book", "Joe Blow", "11111", 1929);
    ldth.ds.put(bookEntity);

    Book book = bp.getBook(bookEntity.getKey());
    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book = :b");
    q.setParameter("b", book);
    List<HasOneToOneJPA> result = (List<HasOneToOneJPA>) q.getResultList();
    assertEquals(1, result.size());
    assertEquals(parentEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
  }

  public void testFilterByChildObject() {
    testFilterByChildObject(new AttachedBookProvider());
    testFilterByChildObject(new TransientBookProvider());
  }

  private void testFilterByChildObject_AdditionalFilterOnParent(BookProvider bp) {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bookEntity = newBookEntity(parentEntity.getKey(), "Bar Book", "Joe Blow", "11111", 1929);
    ldth.ds.put(bookEntity);

    Book book = bp.getBook(bookEntity.getKey());
    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where id = :parentId and book = :b");
    q.setParameter("parentId", KeyFactory.keyToString(bookEntity.getKey()));
    q.setParameter("b", book);
    List<HasOneToOneJPA> result = (List<HasOneToOneJPA>) q.getResultList();
    assertTrue(result.isEmpty());

    q.setParameter("parentId", KeyFactory.keyToString(parentEntity.getKey()));
    q.setParameter("b", book);
    result = (List<HasOneToOneJPA>) q.getResultList();
    assertEquals(1, result.size());
    assertEquals(parentEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
  }

  public void testFilterByChildObject_AdditionalFilterOnParent() {
    testFilterByChildObject_AdditionalFilterOnParent(new AttachedBookProvider());
    testFilterByChildObject_AdditionalFilterOnParent(new TransientBookProvider());
  }


  private void testFilterByChildObject_UnsupportedOperator(BookProvider bp) {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bookEntity = newBookEntity(parentEntity.getKey(), "Bar Book", "Joe Blow", "11111", 1929);
    ldth.ds.put(bookEntity);

    Book book = bp.getBook(bookEntity.getKey());
    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book > :b");
    q.setParameter("b", book);
    try {
      q.getResultList();
      fail("expected udfe");
    } catch (DatastoreQuery.UnsupportedDatastoreFeatureException udfe) {
      // good
    }
  }

  public void testFilterByChildObject_UnsupportedOperator() {
    testFilterByChildObject_UnsupportedOperator(new AttachedBookProvider());
    testFilterByChildObject_UnsupportedOperator(new TransientBookProvider());
  }

  private void testFilterByChildObject_ValueWithoutAncestor(BookProvider bp) {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "11111", 1929);
    ldth.ds.put(bookEntity);

    Book book = bp.getBook(bookEntity.getKey());
    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book = :b");
    q.setParameter("b", book);
    try {
      q.getResultList();
      fail("expected JPAException");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testFilterByChildObject_ValueWithoutAncestor() {
    testFilterByChildObject_ValueWithoutAncestor(new AttachedBookProvider());
    testFilterByChildObject_ValueWithoutAncestor(new TransientBookProvider());
  }

  public void testFilterByChildObject_KeyIsWrongType() {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);

    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book = :b");
    q.setParameter("b", parentEntity.getKey());
    try {
      q.getResultList();
      fail("expected JPAException");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testFilterByChildObject_KeyParentIsWrongType() {
    Key parent = KeyFactory.createKey("yar", 44);
    Entity bookEntity = new Entity(Book.class.getSimpleName(), parent);

    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book = :b");
    q.setParameter("b", bookEntity.getKey());
    try {
      q.getResultList();
      fail("expected JPAException");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testFilterByChildObject_ValueWithoutId() {
    Entity parentEntity = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "11111", 1929);
    ldth.ds.put(bookEntity);

    Book book = new Book();
    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book = :b");
    q.setParameter("b", book);
    try {
      q.getResultList();
      fail("expected JPAException");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testFilterByParentObject() {
    Entity parentEntity = new Entity(HasOneToManyListJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity
        bidirEntity =
        new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity);
    Entity
        bidirEntity2 =
        new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity2);

    HasOneToManyListJPA parent =
        em.find(HasOneToManyListJPA.class, KeyFactory.keyToString(parentEntity.getKey()));
    Query q = em.createQuery("SELECT FROM " +
                             BidirectionalChildListJPA.class.getName() +
                             " WHERE parent = :p");

    q.setParameter("p", parent);
    @SuppressWarnings("unchecked")
    List<BidirectionalChildListJPA> result = (List<BidirectionalChildListJPA>) q.getResultList();
    assertEquals(2, result.size());
    assertEquals(bidirEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
    assertEquals(bidirEntity2.getKey(), KeyFactory.stringToKey(result.get(1).getId()));
  }

  public void testFilterByParentId() {
    Entity parentEntity = new Entity(HasOneToManyListJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity
        bidirEntity =
        new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity);
    Entity
        bidirEntity2 =
        new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity2);

    HasOneToManyListJPA parent =
        em.find(HasOneToManyListJPA.class, KeyFactory.keyToString(parentEntity.getKey()));
    Query q = em.createQuery("SELECT FROM " +
                             BidirectionalChildListJPA.class.getName() +
                             " WHERE parent = :p");

    q.setParameter("p", parent.getId());
    @SuppressWarnings("unchecked")
    List<BidirectionalChildListJPA> result = (List<BidirectionalChildListJPA>) q.getResultList();
    assertEquals(2, result.size());
    assertEquals(bidirEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
    assertEquals(bidirEntity2.getKey(), KeyFactory.stringToKey(result.get(1).getId()));
  }

  public void testFilterByParentKey() {
    Entity parentEntity = new Entity(HasOneToManyListJPA.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity
        bidirEntity =
        new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity);
    Entity
        bidirEntity2 =
        new Entity(BidirectionalChildListJPA.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity2);

    Query q = em.createQuery("SELECT FROM " +
                             BidirectionalChildListJPA.class.getName() +
                             " WHERE parent = :p");

    q.setParameter("p", parentEntity.getKey());
    @SuppressWarnings("unchecked")
    List<BidirectionalChildListJPA> result = (List<BidirectionalChildListJPA>) q.getResultList();
    assertEquals(2, result.size());
    assertEquals(bidirEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
    assertEquals(bidirEntity2.getKey(), KeyFactory.stringToKey(result.get(1).getId()));
  }

  public void testFilterByMultiValueProperty() {
    Entity entity = new Entity(HasMultiValuePropsJPA.class.getSimpleName());
    entity.setProperty("strList", Utils.newArrayList("1", "2", "3"));
    entity.setProperty("keyList",
                       Utils.newArrayList(KeyFactory.createKey("be", "bo"),
                                          KeyFactory.createKey("bo", "be")));
    ldth.ds.put(entity);

    Query q = em.createQuery(
        "select from " + HasMultiValuePropsJPA.class.getName()
        + " where strList = :p1 AND strList = :p2");
    q.setParameter("p1", "1");
    q.setParameter("p2", "3");
    @SuppressWarnings("unchecked")
    List<HasMultiValuePropsJPA> result = (List<HasMultiValuePropsJPA>) q.getResultList();
    assertEquals(1, result.size());
    q.setParameter("p1", "1");
    q.setParameter("p2", "4");
    @SuppressWarnings("unchecked")
    List<HasMultiValuePropsJPA> result2 = (List<HasMultiValuePropsJPA>) q.getResultList();
    assertEquals(0, result2.size());

    q = em.createQuery(
        "select from " + HasMultiValuePropsJPA.class.getName()
        + " where keyList = :p1 AND keyList = :p2");
    q.setParameter("p1", KeyFactory.createKey("be", "bo"));
    q.setParameter("p2", KeyFactory.createKey("bo", "be"));
    assertEquals(1, result.size());
    q.setParameter("p1", KeyFactory.createKey("be", "bo"));
    q.setParameter("p2", KeyFactory.createKey("bo", "be2"));
    @SuppressWarnings("unchecked")
    List<HasMultiValuePropsJPA> result3 = (List<HasMultiValuePropsJPA>) q.getResultList();
    assertEquals(0, result3.size());
  }

  public void testFilterByEmbeddedField() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    Query q = em.createQuery(
        "select from " + Person.class.getName() + " where name.first = \"max\"");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testFilterByEmbeddedField_OverriddenColumn() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    Query q = em.createQuery(
        "select from " + Person.class.getName()
        + " where anotherName.last = \"notross\"");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testFilterByEmbeddedField_MultipleFields() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "max");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    Query q = em.createQuery(
        "select from " + Person.class.getName()
        + " where name.first = \"max\" AND anotherName.last = \"notross\"");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testFilterBySubObject_UnknownField() {
    try {
      em.createQuery(
          "select from " + Flight.class.getName() + " where origin.first = \"max\"")
          .getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testFilterBySubObject_NotEmbeddable() {
    try {
      em.createQuery(
          "select from " + HasOneToOneJPA.class.getName() + " where flight.origin = \"max\"")
          .getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testSortByEmbeddedField() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max2");
    entity.setProperty("last", "ross2");
    entity.setProperty("anotherFirst", "notmax2");
    entity.setProperty("anotherLast", "notross2");
    ldth.ds.put(entity);

    Query q = em.createQuery("select from " + Person.class.getName() + " order by name.first desc");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.getResultList();
    assertEquals(2, result.size());
    assertEquals("max2", result.get(0).getName().getFirst());
    assertEquals("max", result.get(1).getName().getFirst());
  }

  public void testSortByEmbeddedField_OverriddenColumn() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max2");
    entity.setProperty("last", "ross2");
    entity.setProperty("anotherFirst", "notmax2");
    entity.setProperty("anotherLast", "notross2");
    ldth.ds.put(entity);

    Query q =
        em.createQuery("select from " + Person.class.getName() + " order by anotherName.last desc");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.getResultList();
    assertEquals(2, result.size());
    assertEquals("notross2", result.get(0).getAnotherName().getLast());
    assertEquals("notross", result.get(1).getAnotherName().getLast());
  }

  public void testSortByEmbeddedField_MultipleFields() {
    Entity entity0 = new Entity(Person.class.getSimpleName());
    entity0.setProperty("first", "max");
    entity0.setProperty("last", "ross");
    entity0.setProperty("anotherFirst", "notmax");
    entity0.setProperty("anotherLast", "z");
    ldth.ds.put(entity0);

    Entity entity1 = new Entity(Person.class.getSimpleName());
    entity1.setProperty("first", "max");
    entity1.setProperty("last", "ross2");
    entity1.setProperty("anotherFirst", "notmax2");
    entity1.setProperty("anotherLast", "notross2");
    ldth.ds.put(entity1);

    Entity entity2 = new Entity(Person.class.getSimpleName());
    entity2.setProperty("first", "a");
    entity2.setProperty("last", "b");
    entity2.setProperty("anotherFirst", "c");
    entity2.setProperty("anotherLast", "d");
    ldth.ds.put(entity2);

    Query q = em.createQuery(
        "select from " + Person.class.getName()
        + " order by name.first asc, anotherName.last desc");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.getResultList();
    assertEquals(3, result.size());
    assertEquals(Long.valueOf(entity2.getKey().getId()), result.get(0).getId());
    assertEquals(Long.valueOf(entity0.getKey().getId()), result.get(1).getId());
    assertEquals(Long.valueOf(entity1.getKey().getId()), result.get(2).getId());
  }

  public void testSortBySubObject_UnknownField() {
    try {
      em.createQuery(
          "select from " + Book.class.getName() + " order by author.first").getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testSortBySubObject_NotEmbeddable() {
    try {
      em.createQuery(
          "select from " + HasOneToOneJPA.class.getName() + " order by book.author")
          .getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testBigDecimalQuery() {
    Entity e = KitchenSink.newKitchenSinkEntity("blarg", null);
    ldth.ds.put(e);

    Query q = em.createQuery(
        "select from " + KitchenSink.class.getName() + " where bigDecimal = :bd");
    q.setParameter("bd", new BigDecimal(2.444d));
    @SuppressWarnings("unchecked")
    List<KitchenSink> results = (List<KitchenSink>) q.getResultList();
    assertEquals(1, results.size());
  }

  public void testQueryWithNegativeLiteralLong() {
    ldth.ds.put(newBookEntity(null, "title", "auth", "123432", -40));

    Query q = em.createQuery(
        "select from " + Book.class.getName() + " where firstPublished = -40");
    @SuppressWarnings("unchecked")
    List<Book> results = (List<Book>) q.getResultList();
    assertEquals(1, results.size());
    q = em.createQuery(
        "select from " + Book.class.getName() + " where firstPublished > -41");
    @SuppressWarnings("unchecked")
    List<Book> results2 = (List<Book>) q.getResultList();
    assertEquals(1, results2.size());
  }

  public void testQueryWithNegativeLiteralDouble() {
    Entity e = new Entity(HasDoubleJPA.class.getSimpleName());
    e.setProperty("aDouble", -2.23d);
    ldth.ds.put(e);

    Query q = em.createQuery(
        "select from " + HasDoubleJPA.class.getName() + " where aDouble > -2.25");
    @SuppressWarnings("unchecked")
    List<KitchenSink> results = (List<KitchenSink>) q.getResultList();
    assertEquals(1, results.size());
  }

  public void testQueryWithNegativeParam() {
    ldth.ds.put(newBookEntity(null, "title", "auth", "123432", -40));

    Query q = em.createQuery(
        "select from " + Book.class.getName() + " where firstPublished = :p");
    q.setParameter("p", -40);
    @SuppressWarnings("unchecked")
    List<Book> results = (List<Book>) q.getResultList();
    assertEquals(1, results.size());
  }

  public void testKeyQueryWithUnencodedStringPk() {
    Entity e = new Entity(HasUnencodedStringPkJPA.class.getSimpleName(), "yar");
    ldth.ds.put(e);
    Query q = em.createQuery(
        "select from " + HasUnencodedStringPkJPA.class.getName() + " where id = :p");
    q.setParameter("p", e.getKey().getName());
    @SuppressWarnings("unchecked")
    List<HasUnencodedStringPkJPA> results =
        (List<HasUnencodedStringPkJPA>) q.getResultList();
    assertEquals(1, results.size());
    assertEquals(e.getKey().getName(), results.get(0).getId());

    q = em.createQuery(
        "select from " + HasUnencodedStringPkJPA.class.getName() + " where id = :p");
    q.setParameter("p", e.getKey());
    @SuppressWarnings("unchecked")
    List<HasUnencodedStringPkJPA> results2 =
        (List<HasUnencodedStringPkJPA>) q.getResultList();
    assertEquals(1, results2.size());
    assertEquals(e.getKey().getName(), results2.get(0).getId());
  }

  public void testKeyQueryWithLongPk() {
    Entity e = new Entity(HasLongPkJPA.class.getSimpleName());
    ldth.ds.put(e);
    Query q = em.createQuery(
        "select from " + HasLongPkJPA.class.getName() + " where id = :p");
    q.setParameter("p", e.getKey().getId());
    @SuppressWarnings("unchecked")
    List<HasLongPkJPA> results = (List<HasLongPkJPA>) q.getResultList();
    assertEquals(1, results.size());
    assertEquals(Long.valueOf(e.getKey().getId()), results.get(0).getId());

    q = em.createQuery(
        "select from " + HasLongPkJPA.class.getName() + " where id = :p");
    q.setParameter("p", e.getKey().getId());
    @SuppressWarnings("unchecked")
    List<HasLongPkJPA> results2 = (List<HasLongPkJPA>) q.getResultList();
    assertEquals(1, results2.size());
    assertEquals(Long.valueOf(e.getKey().getId()), results2.get(0).getId());
  }

  public void testQuerySingleResult_OneResult() {
    Entity e = newBook("t1", "max", "12345");
    ldth.ds.put(e);
    Query q = em.createQuery(
        "select from " + Book.class.getName() + " where title = :p");
    q.setParameter("p", "t1");
    Book pojo = (Book) q.getSingleResult();
    assertEquals(e.getKey(), KeyFactory.stringToKey(pojo.getId()));
  }

  public void testQuerySingleResult_NoResult() {
    Entity e = newBook("t1", "max", "12345");
    ldth.ds.put(e);
    Query q = em.createQuery(
        "select from " + Book.class.getName() + " where title = :p");
    q.setParameter("p", "not t1");
    try {
      q.getSingleResult();
      fail("expected exception");
    } catch (NoResultException ex) {
      // good
    }
  }

  public void testQuerySingleResult_MultipleResults() {
    Entity e1 = newBook("t1", "max", "12345");
    Entity e2 = newBook("t1", "max", "12345");
    ldth.ds.put(e1);
    ldth.ds.put(e2);
    Query q = em.createQuery(
        "select from " + Book.class.getName() + " where title = :p");
    q.setParameter("p", "t1");
    try {
      q.getSingleResult();
      fail("expected exception");
    } catch (NonUniqueResultException ex) {
      // good
    }
  }


  public void testSortByUnknownProperty() {
    try {
      em.createQuery("select from " + Book.class.getName() + " order by dne").getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testDatastoreFailureWhileIterating() {
    ExceptionThrowingDatastoreDelegate.ExceptionPolicy policy =
        new ExceptionThrowingDatastoreDelegate.BaseExceptionPolicy() {
          int count = 0;

          protected void doIntercept(String methodName) {
            count++;
            if (count == 3) {
              throw new DatastoreFailureException("boom");
            }
          }
        };

    ExceptionThrowingDatastoreDelegate dd =
        new ExceptionThrowingDatastoreDelegate(ApiProxy.getDelegate(), policy);
    ApiProxy.setDelegate(dd);
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName() + " where id = :key");
    q.setParameter("key", KeyFactory.keyToString(bookEntity.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    try {
      books.size();
      fail("expected exception");
    } catch (NucleusDataStoreException e) { // DataNuc bug - they should be wrapping with JPA exceptions
      // good
      assertTrue(e.getCause() instanceof DatastoreFailureException);
    }
  }

  public void testBadRequest() {
    ExceptionThrowingDatastoreDelegate.ExceptionPolicy policy =
        new ExceptionThrowingDatastoreDelegate.BaseExceptionPolicy() {
          int count = 0;

          protected void doIntercept(String methodName) {
            count++;
            if (count == 1) {
              throw new IllegalArgumentException("boom");
            }
          }
        };
    ExceptionThrowingDatastoreDelegate dd =
        new ExceptionThrowingDatastoreDelegate(ApiProxy.getDelegate(), policy);
    ApiProxy.setDelegate(dd);

    Query q = em.createQuery("select from " + Book.class.getName());
    try {
      q.getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
      assertTrue(e.getCause() instanceof IllegalArgumentException);
    }
  }

  public void testCountQuery() {
    Entity e1 = newBook("the title", "jimmy", "12345", 2003);
    Entity e2 = newBook("the title", "jimmy", "12345", 2004);
    ldth.ds.put(e1);
    ldth.ds.put(e2);
    Query q = em.createQuery("select count(id) from " + Book.class.getName());
    assertEquals(2, q.getSingleResult());
  }

  public void testCountQueryWithFilter() {
    Entity e1 = newBook("the title", "jimmy", "12345", 2003);
    Entity e2 = newBook("the title", "jimmy", "12345", 2004);
    ldth.ds.put(e1);
    ldth.ds.put(e2);
    Query
        q =
        em.createQuery(
            "select count(id) from " + Book.class.getName() + " where firstPublished = 2003");
    assertEquals(1, q.getSingleResult());
  }

  public void testCountQueryWithUnknownCountProp() {
    Entity e1 = newBook("the title", "jimmy", "12345", 2003);
    Entity e2 = newBook("the title", "jimmy", "12345", 2004);
    ldth.ds.put(e1);
    ldth.ds.put(e2);
    // letting this go through intentionally
    // we may want to circle back and lock this down but for now it's really
    // not a big deal
    Query q = em.createQuery("select count(doesnotexist) from " + Book.class.getName());
    assertEquals(2, q.getSingleResult());
  }

  public void testCountQueryWithOffsetFails() {
    Entity e1 = newBook("the title", "jimmy", "12345", 2003);
    Entity e2 = newBook("the title", "jimmy", "12345", 2004);
    ldth.ds.put(e1);
    ldth.ds.put(e2);
    Query q = em.createQuery("select count(id) from " + Book.class.getName());
    q.setFirstResult(1);
    try {
      q.getSingleResult();
      fail("expected exception");
    } catch (UnsupportedOperationException uoe) {
      // good
    }
  }

  public void testQueryCacheDisabled() {
    ObjectManager om = ((EntityManagerImpl) em).getObjectManager();
    JDOQLQuery q = new JDOQLQuery(om, "select from " + Book.class.getName());
    assertFalse(q.getBooleanExtensionProperty("datanucleus.query.cached"));
  }

  public void testFilterByEnum_ProvideStringExplicitly() {
    Entity e = new Entity(HasEnumJPA.class.getSimpleName());
    e.setProperty("myEnum", HasEnumJPA.MyEnum.V1.name());
    ldth.ds.put(e);
    Query q = em.createQuery("select from " + HasEnumJPA.class.getName() + " where myEnum = :p1");
    q.setParameter("p1", HasEnumJPA.MyEnum.V1.name());
    List<HasEnumJPA> result = (List<HasEnumJPA>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testFilterByEnum_ProvideEnumExplicitly() {
    Entity e = new Entity(HasEnumJPA.class.getSimpleName());
    e.setProperty("myEnum", HasEnumJPA.MyEnum.V1.name());
    ldth.ds.put(e);
    Query q = em.createQuery("select from " + HasEnumJPA.class.getName() + " where myEnum = :p1");
    q.setParameter("p1", HasEnumJPA.MyEnum.V1);
    List<HasEnumJPA> result = (List<HasEnumJPA>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testFilterByEnum_ProvideLiteral() {
    Entity e = new Entity(HasEnumJPA.class.getSimpleName());
    e.setProperty("myEnum", HasEnumJPA.MyEnum.V1.name());
    ldth.ds.put(e);
    Query q = em.createQuery(
        "select from " + HasEnumJPA.class.getName() + " where myEnum = '"
        + HasEnumJPA.MyEnum.V1.name() + "'");
    List<HasEnumJPA> result = (List<HasEnumJPA>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testFilterByShortBlob() {
    Entity e = new Entity(HasBytesJPA.class.getSimpleName());
    e.setProperty("onePrimByte", 8L);
    e.setProperty("shortBlob", new ShortBlob("short blob".getBytes()));
    ldth.ds.put(e);
    Query
        q =
        em.createQuery("select from " + HasBytesJPA.class.getName() + " where shortBlob = :p1");
    q.setParameter("p1", new ShortBlob("short blob".getBytes()));
    List<HasBytesJPA> result = (List<HasBytesJPA>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testFilterByPrimitiveByteArray() {
    Entity e = new Entity(HasBytesJPA.class.getSimpleName());
    e.setProperty("onePrimByte", 8L);
    e.setProperty("primBytes", new ShortBlob("short blob".getBytes()));
    ldth.ds.put(e);
    Query
        q =
        em.createQuery("select from " + HasBytesJPA.class.getName() + " where primBytes = :p1");
    q.setParameter("p1", "short blob".getBytes());
    List<HasBytesJPA> result = (List<HasBytesJPA>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testFilterByByteArray() {
    Entity e = new Entity(HasBytesJPA.class.getSimpleName());
    e.setProperty("onePrimByte", 8L);
    e.setProperty("bytes", new ShortBlob("short blob".getBytes()));
    ldth.ds.put(e);
    Query q = em.createQuery("select from " + HasBytesJPA.class.getName() + " where bytes = :p1");
    q.setParameter("p1", PrimitiveArrays.asList("short blob".getBytes()).toArray(new Byte[0]));
    List<HasBytesJPA> result = (List<HasBytesJPA>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testAliasedFilter() {
    Entity bookEntity = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName() + " b where b.id = :key");
    q.setParameter("key", KeyFactory.keyToString(bookEntity.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(1, books.size());
    assertEquals(bookEntity.getKey(), KeyFactory.stringToKey(books.get(0).getId()));
  }

  public void testAliasedSort() {
    Entity bookEntity1 = newBook("Bar Book", "Joe Blow", "67891");
    Entity bookEntity2 = newBook("Bar Book", "Joe Blow", "67890");
    ldth.ds.put(bookEntity1);
    ldth.ds.put(bookEntity2);

    javax.persistence.Query q = em.createQuery(
        "select from " + Book.class.getName() + " b order by b.isbn");
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(2, books.size());
    assertEquals(bookEntity2.getKey(), KeyFactory.stringToKey(books.get(0).getId()));
    assertEquals(bookEntity1.getKey(), KeyFactory.stringToKey(books.get(1).getId()));
  }

  public void testAliasedEmbeddedFilter() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    Query q = em.createQuery(
        "select from " + Person.class.getName() + " p where p.name.first = \"max\"");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.getResultList();
    assertEquals(1, result.size());
  }

  public void testAliasedEmbeddedSort() {
    Entity entity1 = new Entity(Person.class.getSimpleName());
    entity1.setProperty("first", "max");
    entity1.setProperty("last", "ross");
    entity1.setProperty("anotherFirst", "notmax2");
    entity1.setProperty("anotherLast", "notross");
    ldth.ds.put(entity1);
    Entity entity2 = new Entity(Person.class.getSimpleName());
    entity2.setProperty("first", "max");
    entity2.setProperty("last", "ross");
    entity2.setProperty("anotherFirst", "notmax1");
    entity2.setProperty("anotherLast", "notross");
    ldth.ds.put(entity2);

    Query q = em.createQuery(
        "select from " + Person.class.getName() + " p order by p.anotherName.first");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.getResultList();
    assertEquals(2, result.size());
    assertEquals(entity2.getKey(), TestUtils.createKey(Person.class, result.get(0).getId()));
    assertEquals(entity1.getKey(), TestUtils.createKey(Person.class, result.get(1).getId()));
  }

  public void testFilterByNullValue_Literal() {
    Entity e = new Entity(NullDataJPA.class.getSimpleName());
    e.setProperty("string", null);
    ldth.ds.put(e);

    Query q = em.createQuery("select from " + NullDataJPA.class.getName() + " where string = null");
    @SuppressWarnings("unchecked")
    List<NullDataJPA> results = (List<NullDataJPA>) q.getResultList();
    assertEquals(1, results.size());
  }

  public void testFilterByNullValue_Param() {
    Entity e = new Entity(NullDataJPA.class.getSimpleName());
    e.setProperty("string", null);
    ldth.ds.put(e);

    Query q = em.createQuery("select from " + NullDataJPA.class.getName() + " where string = :p");
    q.setParameter("p", null);
    @SuppressWarnings("unchecked")
    List<NullDataJPA> results = (List<NullDataJPA>) q.getResultList();
    assertEquals(1, results.size());
  }

  public void testQueryForOneToManySetWithKeyPk() {
    Entity e = new Entity(HasOneToManyKeyPkSetJPA.class.getSimpleName());
    ldth.ds.put(e);

    beginTxn();
    Query q = em.createQuery("select from " + HasOneToManyKeyPkSetJPA.class.getName());
    @SuppressWarnings("unchecked")
    List<HasOneToManyKeyPkSetJPA> results = q.getResultList();
    assertEquals(1, results.size());
    assertEquals(0, results.get(0).getBooks().size());
    commitTxn();
  }

  public void testQueryForOneToManyListWithKeyPk() {
    Entity e = new Entity(HasOneToManyKeyPkListJPA.class.getSimpleName());
    ldth.ds.put(e);

    beginTxn();
    Query q = em.createQuery("select from " + HasOneToManyKeyPkListJPA.class.getName());
    @SuppressWarnings("unchecked")
    List<HasOneToManyKeyPkListJPA> results = q.getResultList();
    assertEquals(1, results.size());
    assertEquals(0, results.get(0).getBooks().size());
    commitTxn();
  }

  public void testQueryForOneToManySetWithLongPk() {
    Entity e = new Entity(HasOneToManyLongPkSetJPA.class.getSimpleName());
    ldth.ds.put(e);

    beginTxn();
    Query q = em.createQuery("select from " + HasOneToManyLongPkSetJPA.class.getName());
    @SuppressWarnings("unchecked")
    List<HasOneToManyLongPkSetJPA> results = q.getResultList();
    assertEquals(1, results.size());
    assertEquals(0, results.get(0).getBooks().size());
    commitTxn();
  }

  public void testQueryForOneToManyListWithLongPk() {
    Entity e = new Entity(HasOneToManyLongPkListJPA.class.getSimpleName());
    ldth.ds.put(e);

    beginTxn();
    Query q = em.createQuery("select from " + HasOneToManyLongPkListJPA.class.getName());
    @SuppressWarnings("unchecked")
    List<HasOneToManyLongPkListJPA> results = q.getResultList();
    assertEquals(1, results.size());
    assertEquals(0, results.get(0).getBooks().size());
    commitTxn();
  }

  public void testQueryForOneToManySetWithUnencodedStringPk() {
    Entity e = new Entity(HasOneToManyUnencodedStringPkSetJPA.class.getSimpleName(), "yar");
    ldth.ds.put(e);

    beginTxn();
    Query q = em.createQuery("select from " + HasOneToManyUnencodedStringPkSetJPA.class.getName());
    @SuppressWarnings("unchecked")
    List<HasOneToManyUnencodedStringPkSetJPA> results = q.getResultList();
    assertEquals(1, results.size());
    assertEquals(0, results.get(0).getBooks().size());
    commitTxn();
  }

  public void testQueryForOneToManyListWithUnencodedStringPk() {
    Entity e = new Entity(HasOneToManyUnencodedStringPkListJPA.class.getSimpleName(), "yar");
    ldth.ds.put(e);

    beginTxn();
    Query q = em.createQuery("select from " + HasOneToManyUnencodedStringPkListJPA.class.getName());
    @SuppressWarnings("unchecked")
    List<HasOneToManyUnencodedStringPkListJPA> results = q.getResultList();
    assertEquals(1, results.size());
    assertEquals(0, results.get(0).getBooks().size());
    commitTxn();
  }

  public void testBatchGet_NoTxn() {
    switchDatasource(EntityManagerFactoryName.nontransactional_ds_non_transactional_ops_allowed);
    Entity e1 = newBookEntity(null, "title", "auth", "123432", -40);
    ldth.ds.put(e1);
    Entity e2 = newBookEntity(null, "title", "auth", "123432", -40);
    ldth.ds.put(e2);
    Entity e3 = newBookEntity(null, "title", "auth", "123432", -40);
    ldth.ds.put(e3);

    Key key = KeyFactory.createKey("yar", "does not exist");
    Query q = em.createQuery("select from " + Book.class.getName() + " where id = :ids");
    q.setParameter("ids", Utils.newArrayList(key, e1.getKey(), e2.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(2, books.size());
    Set<Key> keys = Utils.newHashSet(KeyFactory.stringToKey(
        books.get(0).getId()), KeyFactory.stringToKey(books.get(1).getId()));
    assertTrue(keys.contains(e1.getKey()));
    assertTrue(keys.contains(e2.getKey()));
  }

  public void testBatchGet_Count_NoTxn() {
    switchDatasource(EntityManagerFactoryName.nontransactional_ds_non_transactional_ops_allowed);
    Entity e1 = newBookEntity(null, "title", "auth", "123432", -40);
    ldth.ds.put(e1);
    Entity e2 = newBookEntity(null, "title", "auth", "123432", -40);
    ldth.ds.put(e2);
    Entity e3 = newBookEntity(null, "title", "auth", "123432", -40);
    ldth.ds.put(e3);

    Key key = KeyFactory.createKey("yar", "does not exist");
    Query q = em.createQuery("select count(id) from " + Book.class.getName() + " where id = :ids");
    q.setParameter("ids", Utils.newArrayList(key, e1.getKey(), e2.getKey()));
    int count = (Integer) q.getSingleResult();
    assertEquals(2, count);
  }

  public void testBatchGet_Txn() {
    Entity e1 = newBookEntity(null, "title", "auth", "123432", -40);
    ldth.ds.put(e1);
    Entity e2 = newBookEntity(e1.getKey(), "title", "auth", "123432", -40);
    ldth.ds.put(e2);
    Entity e3 = newBookEntity(null, "title", "auth", "123432", -40);
    ldth.ds.put(e3);

    Key key = KeyFactory.createKey(e1.getKey(), "yar", "does not exist");
    Query q = em.createQuery("select from " + Book.class.getName() + " where id = :ids");
    q.setParameter("ids", Utils.newArrayList(key, e1.getKey(), e2.getKey()));
    @SuppressWarnings("unchecked")
    List<Book> books = (List<Book>) q.getResultList();
    assertEquals(2, books.size());
    Set<Key> keys = Utils.newHashSet(KeyFactory.stringToKey(
        books.get(0).getId()), KeyFactory.stringToKey(books.get(1).getId()));
    assertTrue(keys.contains(e1.getKey()));
    assertTrue(keys.contains(e2.getKey()));
  }

  public void testBatchGet_Illegal() {
    switchDatasource(EntityManagerFactoryName.nontransactional_ds_non_transactional_ops_allowed);
    Query q = em.createQuery("select from " + Flight.class.getName() + " where origin = :ids");
    q.setParameter("ids", Utils.newArrayList());
    try {
      q.getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
    q = em.createQuery(
        "select from " + Flight.class.getName() + " where id = :ids and origin = :origin");
    q.setParameter("ids", Utils.newArrayList());
    q.setParameter("origin", "bos");
    try {
      q.getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
    q = em.createQuery(
        "select from " + Flight.class.getName() + " where origin = :origin and id = :ids");
    q.setParameter("origin", "bos");
    q.setParameter("ids", Utils.newArrayList());
    try {
      q.getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
    q = em.createQuery("select from " + Flight.class.getName() + " where id > :ids");
    q.setParameter("ids", Utils.newArrayList());
    try {
      q.getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
    q = em.createQuery("select from " + Flight.class.getName() + " where id = :ids order by id");
    q.setParameter("ids", Utils.newArrayList());
    try {
      q.getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testNamedQuery() {
    // need to persist an instance to force the metadata to load.
    // TODO(maxr) Remove this when we upgrade to DataNuc 1.1.2
    Book b = new Book();
    b.setTitle("yam");
    beginTxn();
    em.persist(b);
    commitTxn();
    Query q = em.createNamedQuery("namedQuery");
    assertEquals(b, q.getResultList().get(0));
  }

  public void testRestrictFetchedFields_UnknownField() {
    Query q = em.createQuery("select dne from " + Book.class.getName());
    try {
      q.getResultList();
      fail("expected exception");
    } catch (PersistenceException e) {
      // good
    }
  }

  public void testRestrictFetchedFields_OneField() {
    Entity e1 = Book.newBookEntity("author", "12345", "the title");
    ldth.ds.put(e1);
    Query q = em.createQuery("select title from " + Book.class.getName());
    @SuppressWarnings("unchecked")
    List<String> titles = (List<String>) q.getResultList();
    assertEquals(1, titles.size());
    assertEquals("the title", titles.get(0));

    Entity e2 = Book.newBookEntity("another author", "123456", "the other title");
    ldth.ds.put(e2);

    @SuppressWarnings("unchecked")
    List<String> titles2 = (List<String>) q.getResultList();
    assertEquals(2, titles2.size());
    assertEquals("the title", titles2.get(0));
    assertEquals("the other title", titles2.get(1));

    q = em.createQuery("select id from " + Book.class.getName());
    @SuppressWarnings("unchecked")
    List<String> ids = (List<String>) q.getResultList();
    assertEquals(2, ids.size());
    assertEquals(KeyFactory.keyToString(e1.getKey()), ids.get(0));
    assertEquals(KeyFactory.keyToString(e2.getKey()), ids.get(1));
  }

  public void testRestrictFetchedFields_TwoFields() {
    Entity e1 = Book.newBookEntity("author", "12345", "the title");
    ldth.ds.put(e1);
    Query q = em.createQuery("select author, isbn from " + Book.class.getName());
    @SuppressWarnings("unchecked")
    List<Object[]> results = (List<Object[]>) q.getResultList();
    assertEquals(1, results.size());
    assertEquals(2, results.get(0).length);
    assertEquals("author", results.get(0)[0]);
    assertEquals("12345", results.get(0)[1]);

    Entity e2 = Book.newBookEntity("another author", null, "the other title");
    ldth.ds.put(e2);

    @SuppressWarnings("unchecked")
    List<Object[]> results2 = (List<Object[]>) q.getResultList();
    assertEquals(2, results2.size());
    assertEquals(2, results2.get(0).length);
    assertEquals("author", results2.get(0)[0]);
    assertEquals("12345", results2.get(0)[1]);
    assertEquals(2, results2.get(0).length);
    assertEquals("another author", results2.get(1)[0]);
    assertNull(results2.get(1)[1]);
  }

  public void testRestrictFetchedFields_OneToOne() {
    Entity e1 = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(e1);
    Entity e2 = Book.newBookEntity(e1.getKey(), "author", "12345", "the title");
    ldth.ds.put(e2);
    Query q = em.createQuery("select id, book from " + HasOneToOneJPA.class.getName());
    @SuppressWarnings("unchecked")
    List<Object[]> results = (List<Object[]>) q.getResultList();
    assertEquals(1, results.size());
    assertEquals(2, results.get(0).length);
    assertEquals(KeyFactory.keyToString(e1.getKey()), results.get(0)[0]);
    Book b = em.find(Book.class, e2.getKey());
    assertEquals(b, results.get(0)[1]);
  }

  public void testRestrictFetchedFields_OneToMany() {
    Entity e1 = new Entity(HasOneToManyListJPA.class.getSimpleName());
    ldth.ds.put(e1);
    Entity e2 = Book.newBookEntity(e1.getKey(), "author", "12345", "the title");
    ldth.ds.put(e2);
    Query q = em.createQuery("select id, books from " + HasOneToManyListJPA.class.getName());
    @SuppressWarnings("unchecked")
    List<Object[]> results = (List<Object[]>) q.getResultList();
    assertEquals(1, results.size());
    assertEquals(2, results.get(0).length);
    assertEquals(KeyFactory.keyToString(e1.getKey()), results.get(0)[0]);
    Book b = em.find(Book.class, e2.getKey());
    List<Book> books = (List<Book>) results.get(0)[1];
    assertEquals(1, books.size());
    assertEquals(b, books.get(0));
  }

  public void testRestrictFetchedFields_AliasedField() {
    Entity e1 = Book.newBookEntity("author", "12345", "the title");
    ldth.ds.put(e1);
    Query q = em.createQuery("select b.isbn from " + Book.class.getName() + " b");
    @SuppressWarnings("unchecked")
    List<String> isbns = (List<String>) q.getResultList();
    assertEquals(1, isbns.size());
    assertEquals("12345", isbns.get(0));
  }

  public void testRestrictFetchedFieldsAndCount() {
    Entity e1 = Book.newBookEntity("author", "12345", "the title");
    ldth.ds.put(e1);
    Query q = em.createQuery("select count(id), isbn from " + Book.class.getName());
    try {
      q.getResultList();
      fail("expected exception");
    } catch (DatastoreQuery.UnsupportedDatastoreFeatureException e) {
      // good
    }

    q = em.createQuery("select isbn, count(id) from " + Book.class.getName());
    try {
      q.getResultList();
      fail("expected exception");
    } catch (DatastoreQuery.UnsupportedDatastoreFeatureException e) {
      // good
    }
  }

  public void testRestrictFetchedFields_EmbeddedField() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    Query q = em.createQuery("select name.first, anotherName.last from " + Person.class.getName());
    try {
      q.getResultList();
      fail("expected exception");
    } catch (UnsupportedOperationException e) {
      // this will start to fail once we add support for selecting embedded fields
    }
//    @SuppressWarnings("unchecked")
//    List<Object[]> result = (List<Object[]>) q.execute();
//    assertEquals(1, result.size());
  }

  public void testIsNull() {
    Entity e = newBook("title", "author", null);
    ldth.ds.put(e);
    Query q = em.createQuery("select from " + Book.class.getName() + " where isbn is NULL");
    @SuppressWarnings("unchecked")
    List<Book> books = q.getResultList();
    assertEquals(1, books.size());
  }

  public void testIsNullChild() {
    Entity e = new Entity(HasOneToOneJPA.class.getSimpleName());
    ldth.ds.put(e);
    Query q = em.createQuery(
        "select from " + HasOneToOneJPA.class.getName() + " where book is null");
    try {
      q.getResultList();
      fail("expected");
    } catch (PersistenceException pe) {
      // good
    }
  }

  public void testIsNullParent() {
    Entity e = new Entity(HasOneToOneJPA.class.getSimpleName());
    Key key = ldth.ds.put(e);
    e = new Entity(HasOneToOneParentJPA.class.getSimpleName(), key);
    ldth.ds.put(e);
    Query q = em.createQuery(
        "select from " + HasOneToOneParentJPA.class.getName() + " where parent is null");
    try {
      q.getResultList();
      fail("expected");
    } catch (PersistenceException pe) {
      // good
    }
  }

  private static Entity newBook(String title, String author, String isbn) {
    return newBook(title, author, isbn, 2000);
  }

  private static Entity newBook(String title, String author, String isbn, int firstPublished) {
    return newBookEntity(null, title, author, isbn, firstPublished);
  }

  private static Entity newBookEntity(
      Key parentKey, String title, String author, String isbn, int firstPublished) {
    Entity e;
    if (parentKey != null) {
      e = new Entity(Book.class.getSimpleName(), parentKey);
    } else {
      e = new Entity(Book.class.getSimpleName());
    }
    e.setProperty("title", title);
    e.setProperty("author", author);
    e.setProperty("isbn", isbn);
    e.setProperty("first_published", firstPublished);
    return e;
  }

  private void assertQueryUnsupportedByDatastore(String query) {
    Query q = em.createQuery(query);
    try {
      q.getResultList();
      fail("expected IllegalArgumentException for query <" + query + ">");
    } catch (PersistenceException e) {
      // good
    }
  }

  private void assertQueryUnsupportedByOrm(String query,
                                           Expression.Operator unsupportedOp) {
    Query q = em.createQuery(query);
    try {
      q.getResultList();
      fail("expected UnsupportedOperationException for query <" + query + ">");
    } catch (DatastoreQuery.UnsupportedDatastoreOperatorException uoe) {
      // Good.
      // Expression.Operator doesn't override equals
      // so we just compare the string representation.
      assertEquals(unsupportedOp.toString(), uoe.getOperation().toString());
    }
  }

  private void assertQueryUnsupportedByOrm(String query,
                                           Expression.Operator unsupportedOp,
                                           Set<Expression.Operator> unsupportedOps) {
    assertQueryUnsupportedByOrm(query, unsupportedOp);
    unsupportedOps.remove(unsupportedOp);
  }

  private void assertQuerySupported(String query, List<FilterPredicate> addedFilters,
                                    List<SortPredicate> addedSorts, Object... nameVals) {
    javax.persistence.Query q = em.createQuery(query);
    String name = null;
    for (Object nameOrVal : nameVals) {
      if (name == null) {
        name = (String) nameOrVal;
      } else {
        q.setParameter(name, nameOrVal);
        name = null;
      }
    }
    q.getResultList();

    assertEquals(addedFilters, getFilterPredicates(q));
    assertEquals(addedSorts, getSortPredicates(q));
  }

  private DatastoreQuery getDatastoreQuery(javax.persistence.Query q) {
    return ((JPQLQuery) ((JPAQuery) q).getInternalQuery()).getDatastoreQuery();
  }

  private List<FilterPredicate> getFilterPredicates(javax.persistence.Query q) {
    return getDatastoreQuery(q).getLatestDatastoreQuery().getFilterPredicates();
  }

  private List<SortPredicate> getSortPredicates(javax.persistence.Query q) {
    return getDatastoreQuery(q).getLatestDatastoreQuery().getSortPredicates();
  }
}