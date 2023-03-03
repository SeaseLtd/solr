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
package org.apache.solr.search;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.VectorValues;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

public class DenseVectorFieldSource extends ValueSource {
    private final String fieldName;
    private final int vectorDim;
    public DenseVectorFieldSource(String fieldName, int vectorDim) {
        this.fieldName = fieldName;
        this.vectorDim = vectorDim;
    }

    @Override
    public FunctionValues getValues(Map<Object, Object> context, LeafReaderContext readerContext) throws IOException {

        final VectorValues vectorValues = readerContext.reader().getVectorValues(fieldName);
        return new VectorFunctionValues(this){
            float[] defaultVector = null;
            int lastDocID = vectorValues.docID();

            @Override
            public float[] vectorVal(int doc) throws IOException {
                if (exists(doc)){
                    return vectorValues.vectorValue();
                } else {
                    return defaultVector();
                }
            }

            @Override
            public boolean exists(int doc) throws IOException {
                if (doc < lastDocID) {
                    throw new IllegalArgumentException(
                            "docs were sent out-of-order: lastDocID=" + lastDocID + " vs docID=" + doc);
                }
                lastDocID = doc;
                int curDocID = vectorValues.docID();
                if (doc > curDocID) {
                    curDocID = vectorValues.advance(doc);
                }
                return doc == curDocID;
            }

            private float[] defaultVector(){
                if (defaultVector == null){
                    defaultVector = new float[vectorDim];
                    Arrays.fill(defaultVector, 0.f);
                }
                return defaultVector;
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (o.getClass() != DenseVectorFieldSource.class) return false;
        DenseVectorFieldSource other = (DenseVectorFieldSource) o;
        return fieldName.equals(other.fieldName);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() * 31 + fieldName.getClass().hashCode();
    }

    @Override
    public String description() {
        return "denseVector(" + fieldName + ")";
    }
}
