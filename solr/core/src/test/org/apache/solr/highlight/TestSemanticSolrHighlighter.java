/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.highlight;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/** Tests for the UnifiedHighlighter Solr plugin * */
public class TestSemanticSolrHighlighter extends SolrTestCaseJ4 {

  // I just temporarily used the TestUnifiedSolrHighlighter.java and adapted to my case

  @BeforeClass
  public static void beforeClass() throws Exception {
    System.setProperty("filterCache.enabled", "false");
    System.setProperty("queryResultCache.enabled", "false");
    System.setProperty(
        "documentCache.enabled", "true"); // this is why we use this particular solrconfig
    initCore("solrconfig-cache-enable-disable.xml", "schema-unifiedhighlight.xml");
  }

  @AfterClass
  public static void afterClass() {
    System.clearProperty("filterCache.enabled");
    System.clearProperty("queryResultCache.enabled");
    System.clearProperty("documentCache.enabled");
    System.clearProperty("solr.tests.id.stored");
    System.clearProperty("solr.tests.id.docValues");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    clearIndex();
    assertU(adoc("text", "Jane Eyre: An Autobiography", "text2", "Bronte, Charlotte", "id", "1"));
    assertU(
        adoc(
            "text",
            "The Complete Project Gutenberg Works of Jane Austen: A Linked Index of all PG Editions of Jane Austen",
            "text2",
            "Austen, Jane",
            "id",
            "2"));
    assertU(commit());
  }

  public static SolrQueryRequest req(String... params) {
    return SolrTestCaseJ4.req(params, "hl.method", "semantic");
  }

  public void testSingleField() {
    assertQ(
        "simplest test",
        req("q", "jane", "hl", "true", "hl.fl", "text", "hl.model", "modelMXNet"),
        "count(//lst[@name='highlighting']/*)=2",
        "//lst[@name='highlighting']/lst[@name='1']/arr[@name='text']/str='Extractive answer for field: text'",
        "//lst[@name='highlighting']/lst[@name='2']/arr[@name='text']/str='Extractive answer for field: text'");
  }

  public void testTwoFields() {
    assertQ(
        "simplest test",
        req("q", "jane", "hl", "true", "hl.fl", "text text2", "hl.model", "modelMXNet"),
        "count(//lst[@name='highlighting']/*)=2",
        "//lst[@name='highlighting']/lst[@name='1']/arr[@name='text']/str='Extractive answer for field: text'",
        "//lst[@name='highlighting']/lst[@name='1']/arr[@name='text2']/str='Extractive answer for field: text2'",
        "//lst[@name='highlighting']/lst[@name='2']/arr[@name='text']/str='Extractive answer for field: text'",
        "//lst[@name='highlighting']/lst[@name='2']/arr[@name='text2']/str='Extractive answer for field: text2'");
  }

  public void testFieldInQuery() {
    assertQ(
        "simplest test",
        req("q", "text:jane", "hl", "true", "hl.fl", "text", "hl.model", "modelMXNet"),
        "count(//lst[@name='highlighting']/*)=2",
        "//lst[@name='highlighting']/lst[@name='1']/arr[@name='text']/str='Extractive answer for field: text'",
        "//lst[@name='highlighting']/lst[@name='2']/arr[@name='text']/str='Extractive answer for field: text'");
  }
}
