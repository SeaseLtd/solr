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
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DenseVectorConstValueSource extends ValueSource {
    float[] vector;
    public DenseVectorConstValueSource(List<Number> vector) {
        this.vector = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++){
            this.vector[i] = vector.get(i).floatValue();
        }
    }

    @Override
    public FunctionValues getValues(Map<Object, Object> context, LeafReaderContext readerContext) throws IOException {
        return new VectorFunctionValues(this) {
            @Override
            public float[] vectorVal(int doc) {
                return vector;
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DenseVectorConstValueSource)) return false;
        DenseVectorConstValueSource other = (DenseVectorConstValueSource) o;
        return Arrays.equals(vector, other.vector);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() * 31 + Arrays.hashCode(vector);
    }

    @Override
    public String description() {
        return "denseVector(" + Arrays.toString(vector) + ')';
    }
}
