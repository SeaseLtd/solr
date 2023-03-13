package org.apache.solr.search.function;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestDenseVectorFunctionQuery extends SolrTestCaseJ4 {
    String IDField = "id";
    String vectorField = "vector";
    String vectorField2 = "vector2";
    String floatField = "floatField";

    @Before
    public void prepareIndex() throws Exception {
        /* vectorDimension="4" similarityFunction="cosine" */
        initCore("solrconfig-basic.xml", "schema-densevector.xml");

        List<SolrInputDocument> docsToIndex = this.prepareDocs();
        for (SolrInputDocument doc : docsToIndex) {
            assertU(adoc(doc));
        }

        assertU(commit());
    }


    private List<SolrInputDocument> prepareDocs() {
        int docsCount = 6;
        List<SolrInputDocument> docs = new ArrayList<>(docsCount);
        for (int i = 1; i < docsCount + 1; i++) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField(IDField, i);
            docs.add(doc);
        }

        docs.get(0)
                .addField(vectorField, Arrays.asList(1f, 2f, 3f, 4f));
        docs.get(0)
                .addField(vectorField2, Arrays.asList(5f, 4f, 1f, 2f));
        docs.get(0)
                .addField(floatField, 1.4);
        docs.get(1)
                .addField(vectorField, Arrays.asList(1.5f, 2.5f, 3.5f, 4.5f));
        docs.get(1)
                .addField(vectorField2, Arrays.asList(2f, 2f, 1f, 4f));
        docs.get(1)
                .addField(floatField, 2.9);
        docs.get(2)
                .addField(vectorField,
                        Arrays.asList(7.5f, 15.5f, 17.5f, 22.5f));
        docs.get(3)
                .addField(vectorField, Arrays.asList(1.4f, 2.4f, 3.4f, 4.4f));

        return docs;
    }


    @Test
    public void vectorFunctionBetweenTwoConstVector(){
        assertQ(
                req(CommonParams.Q, "{!func} vectorSimilarity(COSINE, [1,2,3], [4,5,6])", "fq", "id:(1 2 3)", "fl", "id, score"),
                "//result[@numFound='" + 3 + "']",
                "//result/doc[1]/float[@name='score'][.='0.97463185']",
                "//result/doc[2]/float[@name='score'][.='0.97463185']",
                "//result/doc[3]/float[@name='score'][.='0.97463185']");

        assertQ(
                req(CommonParams.Q, "{!func} vectorSimilarityScore(COSINE, [1,2,3], [4,5,6])", "fq", "id:(1 2 3)", "fl", "id, score"),
                "//result[@numFound='" + 3 + "']",
                "//result/doc[1]/float[@name='score'][.='0.9873159']",
                "//result/doc[2]/float[@name='score'][.='0.9873159']",
                "//result/doc[3]/float[@name='score'][.='0.9873159']");
    }

    @Test
    public void vectorFunctionBetweenTwoVectorFields(){
        assertQ(
                req(CommonParams.Q, "{!func} vectorSimilarity(DOT_PRODUCT, vector, vector2)", "fq", "id:(1 2)", "fl", "id, score"),
                "//result[@numFound='" + 2 + "']",
                "//result/doc[1]/float[@name='score'][.='29.5']",
                "//result/doc[2]/float[@name='score'][.='24.0']");

        assertQ(
                req(CommonParams.Q, "{!func} vectorSimilarityScore(DOT_PRODUCT, vector, vector2)", "fq", "id:(1 2)", "fl", "id, score"),
                "//result[@numFound='" + 2 + "']",
                "//result/doc[1]/float[@name='score'][.='15.25']",
                "//result/doc[2]/float[@name='score'][.='12.5']");
    }

    @Test
    public void vectorFunctionBetweenVectorFieldsAndConst(){
        assertQ(
                req(CommonParams.Q, "{!func} vectorSimilarity(EUCLIDEAN, vector, [1,5,4,3])", "fq", "id:(1 2)", "fl", "id, score"),
                "//result[@numFound='" + 2 + "']",
                "//result/doc[1]/float[@name='score'][.='11.0']",
                "//result/doc[2]/float[@name='score'][.='9.0']");

        assertQ(
                req(CommonParams.Q, "{!func} vectorSimilarityScore(EUCLIDEAN, [1,5,4,3], vector)", "fq", "id:(1 2)", "fl", "id, score"),
                "//result[@numFound='" + 2 + "']",
                "//result/doc[1]/float[@name='score'][.='0.1']",
                "//result/doc[2]/float[@name='score'][.='0.083333336']");
    }

    @Test
    public void resultOfVectorFunction_shouldWorkAsFloatFunctionInput(){
        assertQ(
                req(CommonParams.Q, "{!func} sum(vectorSimilarity(EUCLIDEAN, vector, [1,5,4,3]), floatField)", "fq", "id:(1 2)", "fl", "id, score"),
                "//result[@numFound='" + 2 + "']",
                "//result/doc[1]/float[@name='score'][.='12.4']",
                "//result/doc[2]/float[@name='score'][.='11.9']");

        assertQ(
                req(CommonParams.Q, "{!func} sub(1.5, vectorSimilarityScore(EUCLIDEAN, [1,5,4,3], vector))", "fq", "id:(1 2)", "fl", "id, score"),
                "//result[@numFound='" + 2 + "']",
                "//result/doc[1]/float[@name='score'][.='1.4166666']",
                "//result/doc[2]/float[@name='score'][.='1.4']");
    }

    @Test
    public void missingVectorFieldOnDoc_shouldUseAllZeroVector(){

        // document 3 does not contain value for vector2
        assertQ(
                req(CommonParams.Q, "{!func} vectorSimilarity(DOT_PRODUCT, [1,5,4,3], vector2)", "fq", "id:(2 3)", "fl", "id, score"),
                "//result[@numFound='" + 2 + "']",
                "//result/doc[1]/float[@name='score'][.='28.0']",
                "//result/doc[2]/float[@name='score'][.='0.0']");

        assertQ(
                req(CommonParams.Q, "{!func} vectorSimilarity(EUCLIDEAN, vector, vector2)", "fq", "id:(2 3)", "fl", "id, score"),
                "//result[@numFound='" + 2 + "']",
                "//result/doc[1]/float[@name='score'][.='1109.0']",
                "//result/doc[2]/float[@name='score'][.='7.0']");
    }

    @Test
    public void vectorQueryInRerankQParser_ShouldRescoreOnlyFirstKResults(){
        assertQ(
                req(CommonParams.Q, "id:(1 2 3 4)",
                        "rq", "{!rerank reRankQuery=$rqq reRankDocs=2 reRankWeight=1}",
                        "rqq", "{!func} vectorSimilarity(EUCLIDEAN, [1,5,4,3], vector)",
                        "fl", "id, score"),
                "//result[@numFound='" + 4 + "']",
                "//result/doc[1]/float[@name='score'][.='11.700202']",
                "//result/doc[2]/float[@name='score'][.='9.700202']",
                "//result/doc[3]/float[@name='score'][.='0.7002023']",
                "//result/doc[4]/float[@name='score'][.='0.7002023']");
    }

    @Test
    public void wrongDimensiongVectors_shouldRaiseException(){
        assertQEx(
                "java.lang.IllegalArgumentException: vector dimensions differ: 3!=4",
                "java.lang.IllegalArgumentException: vector dimensions differ: 3!=4",
                req(CommonParams.Q, "{!func} vectorSimilarity(COSINE, [1,2,3], vector)", "fl", "id, score"),
                SolrException.ErrorCode.BAD_REQUEST);

        assertQEx(
                "java.lang.IllegalArgumentException: vector dimensions differ: 3!=2",
                "java.lang.IllegalArgumentException: vector dimensions differ: 3!=2",
                req(CommonParams.Q, "{!func} vectorSimilarityScore(COSINE, [1,2,3], [1,3])", "fl", "id, score"),
                SolrException.ErrorCode.BAD_REQUEST);
    }

    @Test
    public void wrongTypeValueInVectorFunctions_shouldRaiseException(){
        assertQEx(
                "Wrong parameter type: The input is not a vector value",
                "Wrong parameter type: The input is not a vector value",
                req(CommonParams.Q, "{!func} vectorSimilarityScore(COSINE, 1.2, [1,3])", "fl", "id, score"),
                SolrException.ErrorCode.BAD_REQUEST);

        assertQEx(
                "Wrong parameter type: The input is not a vector value",
                "Wrong parameter type: The input is not a vector value",
                req(CommonParams.Q, "{!func} vectorSimilarityScore(COSINE, floatField, [1,3])", "fl", "id, score"),
                SolrException.ErrorCode.BAD_REQUEST);
    }

    @After
    public void cleanUp() {
        clearIndex();
        deleteCore();
    }

}
