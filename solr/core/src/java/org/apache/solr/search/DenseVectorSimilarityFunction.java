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
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.FloatDocValues;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import static java.util.Optional.ofNullable;

public class DenseVectorSimilarityFunction extends ValueSource {

    private final VectorSimilarityFunction similarityFunction;
    private final ValueSource vector1;
    private final ValueSource vector2;

    public DenseVectorSimilarityFunction(String similarityFunctionName, ValueSource vector1, ValueSource vector2) throws SyntaxError {

        this.similarityFunction =
                ofNullable(similarityFunctionName)
                        .map(value -> VectorSimilarityFunction.valueOf(value.toUpperCase(Locale.ROOT)))
                        .orElseThrow(() -> new SyntaxError("wrong similarity function"));
        this.vector1 = vector1;
        this.vector2 = vector2;
    }

    @Override
    public FunctionValues getValues(Map<Object, Object> context, LeafReaderContext readerContext)
            throws IOException {
        return new FloatDocValues(this) {
            @Override
            public float floatVal(int doc) throws IOException {
                VectorFunctionValues vector1Values = (VectorFunctionValues) vector1.getValues(context, readerContext);
                VectorFunctionValues vector2Values = (VectorFunctionValues)  vector2.getValues(context, readerContext);
                return similarityFunction.compare(vector1Values.vectorVal(doc), vector2Values.vectorVal(doc));
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String description() {
        return null;
    }
}
