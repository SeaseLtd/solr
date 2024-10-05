package org.apache.solr.llm.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.cohere.CohereEmbeddingModel;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.llm.store.EmbeddingModelException;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;


public class EmbeddingModel implements Accountable {
    private static final long BASE_RAM_BYTES =
            RamUsageEstimator.shallowSizeOfInstance(EmbeddingModel.class);
    
    protected final String name;
    private final Map<String, Object> params;
    private DimensionAwareEmbeddingModel embedder;
    private Integer hashCode;

    public static EmbeddingModel getInstance(
            SolrResourceLoader solrResourceLoader,
            String className,
            String name,
            Map<String, Object> params)
            throws EmbeddingModelException {
        try {
            DimensionAwareEmbeddingModel embedder;
            Class<?> modelClass = Class.forName(className);
            var builder = modelClass.getMethod("builder").invoke(null);
            for (String paramName : params.keySet()) {
                switch (paramName) {
                    case "timeout":
                        Duration timeOut = Duration.ofSeconds((Long) params.get(paramName));
                        builder.getClass().getMethod(paramName, Duration.class).invoke(builder, timeOut);
                        break;
                    case "logRequests":
                        builder.getClass().getMethod(paramName, Boolean.class).invoke(builder, params.get(paramName));
                        break;
                    case "logResponses":
                        builder.getClass().getMethod(paramName, Boolean.class).invoke(builder, params.get(paramName));
                        break;
                    case "maxSegmentsPerBatch":
                        builder.getClass().getMethod(paramName, Integer.class).invoke(builder, ((Long)params.get(paramName)).intValue());
                        break;
                    default:
                        builder.getClass().getMethod(paramName, String.class).invoke(builder, params.get(paramName));
                }
            }

            embedder = (DimensionAwareEmbeddingModel) builder.getClass().getMethod("build").invoke(builder);
            return new EmbeddingModel(name, embedder, params);
        } catch (final Exception e) {
             throw new EmbeddingModelException("Model loading failed for " + className, e);
        }
    }
    
    public EmbeddingModel(String name, DimensionAwareEmbeddingModel embedder, Map<String, Object> params) {
        this.name = name;
        this.embedder = embedder;
        this.params = params;
    }

    public float[] floatVectorise(String text){
        Embedding vector = embedder.embed(text).content();
        return vector.vector();
    }

    public byte[] byteVectorise(String text){
        return new byte[0];
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
        if (!(obj instanceof EmbeddingModel)) return false;
        final EmbeddingModel other = (EmbeddingModel) obj;
        return Objects.equals(embedder, other.embedder)
                && Objects.equals(name, other.name);
    }

    public String getName() {
        return name;
    }

    public DimensionAwareEmbeddingModel getEmbedder() {
        return embedder;
    }

    public void setEmbedder(DimensionAwareEmbeddingModel embedder) {
        this.embedder = embedder;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
