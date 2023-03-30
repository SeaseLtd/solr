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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.HighlightParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;

public class SemanticSolrHighlighter extends UnifiedSolrHighlighter {

  @Override
  public NamedList<Object> doHighlighting(
      DocList docs, Query query, SolrQueryRequest req, String[] defaultFields) throws IOException {
    final SolrParams params = req.getParams();

    // get the query (without the field name)
    String queryString = getQuery(params.get("q"));

    // get the model name/format from the request
    String modelName = params.get(HighlightParams.MODEL);

    SolrIndexSearcher searcher = req.getSearcher();

    // if highlighting isn't enabled, then why call doHighlighting?
    if (!isHighlightingEnabled(params)) return null;

    int[] docIDs = toDocIDs(docs);

    // fetch the unique keys
    String[] keys = getUniqueKeys(req.getSearcher(), docIDs);

    // query-time parameters
    String[] fieldNames = getHighlightFields(query, req, defaultFields);

    Map<String, String[]> resultMap =
        highlightAnswer(searcher, queryString, fieldNames, docIDs, modelName);

    return encodeSnippets(keys, fieldNames, resultMap);
  }

  protected Map<String, String[]> highlightAnswer(
      SolrIndexSearcher searcher,
      String query,
      String[] fieldsIn,
      int[] docIdsIn,
      String modelFormat)
      throws IOException {

    if (fieldsIn.length < 1) {
      throw new IllegalArgumentException("fieldsIn must not be empty");
    }
    if (searcher == null) {
      throw new IllegalStateException(
          "This method requires that an indexSearcher was passed in the "
              + "constructor.  Perhaps you mean to call highlightWithoutSearcher?");
    }

    Map<String, String[]> snippets = new HashMap<>(fieldsIn.length);

    for (int i = 0; i < docIdsIn.length; i++) {
      int docID = docIdsIn[i];
      Document document = searcher.doc(docID);
      for (String fieldName : fieldsIn) {
        String[] content =
            extractiveModel(query, fieldName, document.getValues(fieldName), modelFormat);
        if (snippets.containsKey(fieldName)) {
          // If the field exists, append the new content to the existing array
          String[] existingContent = snippets.get(fieldName);
          String[] newContent =
              Arrays.copyOf(existingContent, existingContent.length + content.length);
          System.arraycopy(content, 0, newContent, existingContent.length, content.length);
          snippets.put(fieldName, newContent);
        } else {
          // If the field does not exist, add a new entry to the map with the content array
          snippets.put(fieldName, content);
        }
      }
    }

    return snippets;
  }

  private String[] extractiveModel(
      String query, String fieldName, String[] documentContent, String modelFormat)
      throws IOException {

    // this is just an example - in the plugin we don't use this method but will call the specific
    // method that use
    // the NLP model to extract the answer to highlight from the content. Here I just replace the
    // document
    // field content with a sample string.
    String[] updatedContent = Arrays.copyOf(documentContent, documentContent.length);
    updatedContent[0] = "Extractive answer for field: " + fieldName;

    return updatedContent;
  }

  private String getQuery(String query) {
    // to handle both the conditions (e.g. q=apple or q=text:apple)
    if (query.contains(":")) {
      String[] parts = query.split(":");
      String value = parts[1];
      return value;
    }
    return query;
  }
}
