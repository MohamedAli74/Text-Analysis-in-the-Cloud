package dsp1.WorkerApplication;


import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;

import java.io.InputStream;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

public class StanfordNLP {

    private MaxentTagger posTagger;
    private LexicalizedParser parser;

    public StanfordNLP() {
        try {
            // Extract models from resources to /tmp
            String posModelPath = extractResourceToTemp("/models/english-left3words-distsim.tagger");
            String pcfgModelPath = extractResourceToTemp("/models/englishPCFG.ser.gz");

            // Load the Stanford models
            posTagger = new MaxentTagger(posModelPath);
            parser = LexicalizedParser.loadModel(pcfgModelPath);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load Stanford models", e);
        }
    }

    // Extract model file from JAR resources to a real file (needed by Stanford NLP)
    private String extractResourceToTemp(String resourcePath) throws IOException {
        InputStream in = getClass().getResourceAsStream(resourcePath);

        if (in == null) {
            throw new FileNotFoundException("Resource not found inside JAR: " + resourcePath);
        }

        File temp = File.createTempFile("model", ".tmp");
        temp.deleteOnExit();

        try (FileOutputStream out = new FileOutputStream(temp)) {
            in.transferTo(out);
        }

        return temp.getAbsolutePath();
    }

    // ---- POS tagging ----
    public String analyzePOS(String text) {
        return posTagger.tagString(text);
    }

    // ---- Constituency Parsing (tree) ----
    public String analyzeConstituency(String text) {
        Tree tree = parser.parse(text);
        return tree.toString();
    }

    // ---- Dependency Parsing ----
    public String analyzeDependency(String text) {
        Tree tree = parser.parse(text);

        GrammaticalStructureFactory gsf =
                parser.treebankLanguagePack().grammaticalStructureFactory();

        GrammaticalStructure gs =
                gsf.newGrammaticalStructure(tree);

        return gs.typedDependencies().toString();
    }
}
