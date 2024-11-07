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
package org.apache.solr.llm.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.solr.common.SolrException;
import org.apache.solr.request.SolrQueryRequest;

public class SolrEmbeddingModel implements Accountable {
  private static final long BASE_RAM_BYTES =
      RamUsageEstimator.shallowSizeOfInstance(SolrEmbeddingModel.class);
  public static final String MODELS_STORE_PATH = "/embedding-models";
  
  public static final String TIMEOUT_PARAM = "timeout";
  public static final String MAX_SEGMENTS_PER_BATCH_PARAM = "maxSegmentsPerBatch";
  public static final String MAX_RETRIES_PARAM = "maxRetries";
  public static final String EMBEDDING_MODELS_CACHE = "embeddingModelsCache";

  protected final String name;
  private final Map<String, Object> params;
  private final EmbeddingModel embedder;
  private Integer hashCode;

  public SolrEmbeddingModel(String name, EmbeddingModel embedder, Map<String, Object> params) {
    this.name = name;
    this.embedder = embedder;
    this.params = params;
    this.hashCode = calculateHashCode();
  }

  public static SolrEmbeddingModel getInstance(String embeddingModelName, SolrQueryRequest req) {
    SolrEmbeddingModel cachedModel = (SolrEmbeddingModel) req.getSearcher().cacheLookup(EMBEDDING_MODELS_CACHE, embeddingModelName);
    if(cachedModel == null){
      InputStream jsonModel = getJsonModel(embeddingModelName, req);
      try {
        Map<String, Object> modelParams = new ObjectMapper().readValue(jsonModel, HashMap.class);
        SolrEmbeddingModel embedder = SolrEmbeddingModel.getInstance(modelParams);
        req.getSearcher().cacheInsert(EMBEDDING_MODELS_CACHE, embeddingModelName, embedder);
        return embedder;
      } catch (IOException e) {
        throw new SolrException(
                SolrException.ErrorCode.BAD_REQUEST,
                " The model requested '"
                        + embeddingModelName
                        + "' is not a well formed JSON");}
    } else {
      return cachedModel;
    }
  }

  private static InputStream getJsonModel(String embeddingModelName, SolrQueryRequest req){
    final InputStream[] json = new InputStream[1];
    try {
      req.getCoreContainer().getFileStore().get(
              MODELS_STORE_PATH + "/"+embeddingModelName,
              it -> {
                json[0] = it.getInputStream();
              },
              false);
      return json[0];
    } catch (IOException e) {
      throw new SolrException(
              SolrException.ErrorCode.SERVER_ERROR, "Error getting file from path " + MODELS_STORE_PATH + "/"+embeddingModelName);
    }
  }
  
  private static SolrEmbeddingModel getInstance(Map<String, Object> modelParams) {
    String className = modelParams.get("class").toString();
    String name = modelParams.get("name").toString();
    try {
      EmbeddingModel embedder;
      Map<String, Object> params = (Map<String, Object>) modelParams.get("params");
      Class<?> modelClass = Class.forName(className);
      var builder = modelClass.getMethod("builder").invoke(null);
      if (params != null) {
        for (String paramName : params.keySet()) {
          switch (paramName) {
            case TIMEOUT_PARAM:
              Duration timeOut = Duration.ofSeconds((Long) params.get(paramName));
              builder.getClass().getMethod(paramName, Duration.class).invoke(builder, timeOut);
              break;
            case MAX_SEGMENTS_PER_BATCH_PARAM:
              builder
                      .getClass()
                      .getMethod(paramName, Integer.class)
                      .invoke(builder, ((Long) params.get(paramName)).intValue());
              break;
            case MAX_RETRIES_PARAM:
              builder
                      .getClass()
                      .getMethod(paramName, Integer.class)
                      .invoke(builder, ((Long) params.get(paramName)).intValue());
              break;
            default:
              ArrayList<Method> methods = new ArrayList<>();
              for (var method : builder.getClass().getMethods()) {
                if (paramName.equals(method.getName()) && method.getParameterCount() == 1) {
                  methods.add(method);
                }
              }
              if (methods.size() == 1) {
                methods.get(0).invoke(builder, params.get(paramName));
              } else {
                builder
                        .getClass()
                        .getMethod(paramName, String.class)
                        .invoke(builder, params.get(paramName));
              }
          }
        }
      }
      embedder = (EmbeddingModel) builder.getClass().getMethod("build").invoke(builder);
      return new SolrEmbeddingModel(name, embedder, params);
    } catch (final Exception e) {
      throw new EmbeddingModelException("Model loading failed for " + className, e);
    }
  }

  public float[] vectorise(String text) {
    Embedding vector = embedder.embed(text).content();
    return vector.vector();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(name=" + getName() + ")";
  }

  @Override
  public long ramBytesUsed() {
    return BASE_RAM_BYTES
        + RamUsageEstimator.sizeOfObject(name)
        + RamUsageEstimator.sizeOfObject(embedder);
  }

  @Override
  public int hashCode() {
    if (hashCode == null) {
      hashCode = calculateHashCode();
    }
    return hashCode;
  }

  private int calculateHashCode() {
    final int prime = 31;
    int result = 1;
    result = (prime * result) + Objects.hashCode(name);
    result = (prime * result) + Objects.hashCode(embedder);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof SolrEmbeddingModel)) return false;
    final SolrEmbeddingModel other = (SolrEmbeddingModel) obj;
    return Objects.equals(embedder, other.embedder) && Objects.equals(name, other.name);
  }

  public String getName() {
    return name;
  }

  public EmbeddingModel getEmbedder() {
    return embedder;
  }
  
  public Map<String, Object> getParams() {
    return params;
  }
}
