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
package org.apache.solr.ltr.interleaving;

import java.util.Map;
import java.util.Set;
import org.apache.solr.ltr.LTRScoringQuery;
import org.apache.solr.ltr.LTRThreadModule;
import org.apache.solr.ltr.model.LTRScoringModel;

public class LTRInterleavingScoringQuery extends LTRScoringQuery {

  // Model was picked for this Docs
  private Set<Integer> pickedInterleavingDocIds;

  public LTRInterleavingScoringQuery(LTRScoringModel ltrScoringModel, boolean missingFeatures) {
    super(ltrScoringModel, missingFeatures);
  }

  public LTRInterleavingScoringQuery(LTRScoringModel ltrScoringModel, boolean missingFeatures, boolean extractAllFeatures) {
    super(ltrScoringModel, missingFeatures, extractAllFeatures);
  }

  public LTRInterleavingScoringQuery(
      LTRScoringModel ltrScoringModel,
      Map<String, String[]> externalFeatureInfo,
      boolean missingFeatures,
      boolean extractAllFeatures,
      LTRThreadModule ltrThreadMgr) {
    super(ltrScoringModel, externalFeatureInfo, missingFeatures, extractAllFeatures, ltrThreadMgr);
  }

  public Set<Integer> getPickedInterleavingDocIds() {
    return pickedInterleavingDocIds;
  }

  public void setPickedInterleavingDocIds(Set<Integer> pickedInterleavingDocIds) {
    this.pickedInterleavingDocIds = pickedInterleavingDocIds;
  }
}
