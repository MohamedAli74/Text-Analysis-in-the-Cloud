package dsp1.WorkerApplication;

import dsp1.AWS;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.File;

public class StanfordNLP {

    private static final String BUCKET = "dsp-assignment1-2025111913";

    private MaxentTagger posTagger;
    private LexicalizedParser parser;

    public StanfordNLP() {
        try {
            // 1) Download models from S3 to /tmp
            String posModel = downloadModelFromS3("models/english-left3words-distsim.tagger");
            String pcfgModel = downloadModelFromS3("models/englishPCFG.ser.gz");

            // 2) Load them
            posTagger = new MaxentTagger(posModel);
            parser = LexicalizedParser.loadModel(pcfgModel);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load Stanford models", e);
        }
    }

    // Download model file from S3 to local /tmp dir
    private String downloadModelFromS3(String key) throws Exception {

        File local = new File("/tmp/" + key.replace("/", "_"));
        local.getParentFile().mkdirs();

        AWS.getInstance().getS3().getObject(
                GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(key)
                        .build(),
                local.toPath()
        );

        return local.getAbsolutePath();
    }

    public String analyzePOS(String text) {
        return posTagger.tagString(text);
    }

    public String analyzeConstituency(String text) {
        Tree tree = parser.parse(text);
        return tree.toString();
    }

    public String analyzeDependency(String text) {
        Tree tree = parser.parse(text);

        GrammaticalStructureFactory gsf =
                parser.treebankLanguagePack().grammaticalStructureFactory();

        GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);

        return gs.typedDependencies().toString();
    }
}
