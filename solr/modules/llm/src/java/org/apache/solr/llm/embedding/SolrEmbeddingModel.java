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
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.CaffeineCache;
import org.apache.solr.search.SolrCache;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.IOFunction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrEmbeddingModel implements Accountable {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
  private final Instant fileModTime;
  private Integer hashCode;

  public SolrEmbeddingModel(String name, EmbeddingModel embedder, Map<String, Object> params,
      Instant fileModTime) {
    this.name = name;
    this.embedder = embedder;
    this.params = params;
    this.fileModTime = fileModTime;
    this.hashCode = calculateHashCode();
  }

  @SuppressWarnings("unchecked")
  private static SolrCache<String, SolrEmbeddingModel> getSearcherCache(
      SolrIndexSearcher searcher) {
    return (SolrCache<String, SolrEmbeddingModel>) searcher.getCache(EMBEDDING_MODELS_CACHE);
  }

  @SuppressWarnings("unchecked")
  private static SolrCache<String, SolrEmbeddingModel> getNodeCache(CoreContainer coreContainer) {
    // explicitly configured; return it
    var result = coreContainer.getCache(EMBEDDING_MODELS_CACHE);
    if (result != null) {
      return (SolrCache<String, SolrEmbeddingModel>) result;
    }
    // create one on the fly via the ObjectCache
    return coreContainer
        .getObjectCache()
        .computeIfAbsent(
            EMBEDDING_MODELS_CACHE,
            SolrCache.class,
            key -> new CaffeineCache<String, SolrEmbeddingModel>());
  }

  public static SolrEmbeddingModel getInstance(String embeddingModelName, SolrQueryRequest req) {
    // searcher level cache.  Important to ensure results don't change for the same searcher.
    final var searcherCache = getSearcherCache(req.getSearcher());
    try {
      return searcherCache.computeIfAbsent(
          embeddingModelName, _unused -> getInstance(embeddingModelName, req.getCoreContainer()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static SolrEmbeddingModel getInstance(
      String embeddingModelName, CoreContainer coreContainer) throws IOException {
    // node level cache.  Don't reference the request / core / searcher.
    //  Important for loading a model once on the node if the model hasn't changed.
    //  We also do change detection here.
    final var nodeCache = getNodeCache(coreContainer);
    final var fileStore = coreContainer.getFileStore();
    final var modelInfoPath = fileStore.getRealpath(MODELS_STORE_PATH + "/" + embeddingModelName);

    if (Files.exists(modelInfoPath) == false) {
      throw new SolrException(
          ErrorCode.BAD_REQUEST, "The model '" + embeddingModelName + "' does not exist");
    }

    final IOFunction<String, SolrEmbeddingModel> loadModelFunc =
        _unused -> create(embeddingModelName, modelInfoPath);

    final var lastModifiedTime = Files.getLastModifiedTime(modelInfoPath).toInstant();
    final var model = nodeCache.computeIfAbsent(embeddingModelName, loadModelFunc);
    if (!model.fileModTime.isBefore(lastModifiedTime)) {
      return model;
    }
    // the model is out-of-date.  Clear the cache entry and reload it.

    // synchronize avoids racing threads that clear and load the cache at the same time
    synchronized (model) {
      // maybe the cache entry has been replaced...
      var model2 = nodeCache.get(embeddingModelName);
      if (model != model2) {
        log.debug("Racing threads; model changed {} to {}", model, model2);
        if (model2 != null) {
          return model2; // it has; just return the replacement
        }
        // null; we'll call computeIfAbsent below
      } else {
        log.info("Reloading changed model '{}'", embeddingModelName);
        nodeCache.remove(embeddingModelName);
      }
      return nodeCache.computeIfAbsent(embeddingModelName, loadModelFunc);
    }
  }

  private static @NotNull SolrEmbeddingModel create(String embeddingModelName, Path modelInfoPath) {
    try {
      // given a Path to a JSON file, parse it with Jackson to a Map
      final var modelInfoJson = Files.readString(modelInfoPath);
      @SuppressWarnings("unchecked")
      Map<String, Object> modelInfo = new ObjectMapper().readValue(modelInfoJson, Map.class);
      final var lastModifiedTime = Files.getLastModifiedTime(modelInfoPath).toInstant();
      return SolrEmbeddingModel.create(modelInfo, lastModifiedTime);
    } catch (Exception e) {
      throw new SolrException(
          ErrorCode.SERVER_ERROR, "Model '" + embeddingModelName + "' could not be loaded", e);
    }
  }

  private static SolrEmbeddingModel create(
      Map<String, Object> modelParams, Instant lastModifiedTime) {
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
      return new SolrEmbeddingModel(name, embedder, params, lastModifiedTime);
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
