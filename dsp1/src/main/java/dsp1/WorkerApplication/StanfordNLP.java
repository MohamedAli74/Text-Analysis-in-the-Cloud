package dsp1.WorkerApplication;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.semgraph.SemanticGraph;

import java.util.Properties;

public class StanfordNLP {

    private static final StanfordNLP INSTANCE = new StanfordNLP();

    private final StanfordCoreNLP posPipeline;
    private final StanfordCoreNLP constituencyPipeline;
    private final StanfordCoreNLP dependencyPipeline;

    private StanfordNLP() {
        System.out.println("Initializing Stanford NLP (3 pipelines)...");

        // POS ONLY
        Properties posProps = new Properties();
        posProps.setProperty("annotators", "tokenize,ssplit,pos");
        posPipeline = new StanfordCoreNLP(posProps);

        // Constituent parse (POS + PARSE)
        Properties constProps = new Properties();
        constProps.setProperty("annotators", "tokenize,ssplit,pos,parse");
        constituencyPipeline = new StanfordCoreNLP(constProps);

        // Dependency parse (POS + DEPPARSE)
        Properties depProps = new Properties();
        depProps.setProperty("annotators", "tokenize,ssplit,pos,depparse");
        dependencyPipeline = new StanfordCoreNLP(depProps);

        System.out.println("Stanford NLP is ready!");
    }

    public static StanfordNLP getInstance() {
        return INSTANCE;
    }

    public String analyzePOS(String text) {
        CoreDocument document = new CoreDocument(text);
        posPipeline.annotate(document);

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
        constituencyPipeline.annotate(document);

        if (document.sentences().isEmpty()) return "";

        Tree constituencyParse = document.sentences().get(0).constituencyParse();
        return (constituencyParse != null) ? constituencyParse.toString() : "";
    }

    public String analyzeDependency(String text) {
        CoreDocument document = new CoreDocument(text);
        dependencyPipeline.annotate(document);

        if (document.sentences().isEmpty()) return "";

        SemanticGraph dep = document.sentences().get(0).dependencyParse();
        return (dep != null)
                ? dep.toString(SemanticGraph.OutputFormat.LIST)
                : "";
    }
}