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
package org.apache.solr.ltr;

import org.apache.solr.search.SolrIndexSearcher;

/**
 * FeatureLogger can be registered in a model and provide a strategy for logging the feature values.
 */
public abstract class FeatureLogger {

  /** the name of the cache using for storing the feature value */
  private final String fvCacheName;

  public enum FeatureFormat {
    DENSE,
    SPARSE
  };

  protected final FeatureFormat featureFormat;

  protected FeatureLogger(String fvCacheName, FeatureFormat f) {
    this.fvCacheName = fvCacheName;
    this.featureFormat = f;
  }


  public abstract String makeFeatureVector(LTRScoringQuery.FeatureInfo[] featuresInfo);

}
