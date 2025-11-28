package dsp1.WorkerApplication;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.semgraph.SemanticGraph;

import java.util.Properties;

public class StanfordNLP {

    // instance وحيدة (Singleton)
    private static final StanfordNLP INSTANCE = new StanfordNLP();

    private final StanfordCoreNLP pipeline;

    private StanfordNLP() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,parse,depparse");
        this.pipeline = new StanfordCoreNLP(props);
    }

    public static StanfordNLP getInstance() {
        return INSTANCE;
    }

    public String analyzePOS(String text) {
        CoreDocument document = new CoreDocument(text);
        pipeline.annotate(document);
        StringBuilder result = new StringBuilder();

        document.tokens().forEach(token ->
                result.append(token.word())
                      .append(" (")
                      .append(token.tag())
                      .append(") ")
        );
        return result.toString();
    }

    public String analyzeConstituency(String text) {
        CoreDocument document = new CoreDocument(text);
        pipeline.annotate(document);
        if (document.sentences().isEmpty()) return "";

        CoreSentence sentence = document.sentences().get(0);
        Tree constituencyParse = sentence.constituencyParse();
        return (constituencyParse != null) ? constituencyParse.toString() : "";
    }

    public String analyzeDependency(String text) {
        CoreDocument document = new CoreDocument(text);
        pipeline.annotate(document);
        if (document.sentences().isEmpty()) return "";

        CoreSentence sentence = document.sentences().get(0);
        SemanticGraph dependencyParse = sentence.dependencyParse();
        return (dependencyParse != null)
                ? dependencyParse.toString(SemanticGraph.OutputFormat.LIST)
                : "";
    }
}
