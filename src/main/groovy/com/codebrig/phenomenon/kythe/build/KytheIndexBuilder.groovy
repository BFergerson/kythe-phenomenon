package com.codebrig.phenomenon.kythe.build

import com.codebrig.phenomenon.kythe.KytheIndexObserver
import com.codebrig.phenomenon.kythe.model.KytheIndex
import org.zeroturnaround.exec.ProcessExecutor

/**
 * todo: description
 *
 * @version 0.1
 * @since 0.1
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
class KytheIndexBuilder {

    private final File repositoryDirectory
    static final File javacWrapper = new File("opt/kythe-v0.0.28/extractors/javac-wrapper.sh")
    static final File javacExtractor = new File("opt/kythe-v0.0.28/extractors/javac_extractor.jar")
    static final File javaIndexer = new File("opt/kythe-v0.0.28/indexers/java_indexer.jar")
    static final File dedupStreamTool = new File("opt/kythe-v0.0.28/tools/dedup_stream")
    static final File triplesTool = new File("opt/kythe-v0.0.28/tools/triples")

    //todo: use builder pattern?
    KytheIndexBuilder(File repositoryDirectory) {
        this.repositoryDirectory = Objects.requireNonNull(repositoryDirectory)
    }

    KytheIndex build(List<KytheIndexObserver> indexObservers) throws KytheIndexException {
        def kytheDir = new File("/tmp/stuff/")
        kytheDir.mkdirs()

        def mvnEnvironment = [
                REAL_JAVAC            : "/usr/bin/javac",
                KYTHE_ROOT_DIRECTORY  : repositoryDirectory.absolutePath,
                KYTHE_OUTPUT_DIRECTORY: kytheDir.absolutePath,
                JAVAC_EXTRACTOR_JAR   : javacExtractor.absolutePath
        ]
        def mvnCommand = [
                "mvn",
                "clean",
                "compile",
                "-Dmaven.test.skip=true",
                "-Dmaven.compiler.source=1.8",
                "-Dmaven.compiler.target=1.8",
                "-Dmaven.compiler.fork=true",
                "-Dmaven.compiler.forceJavacCompilerUse=true",
                "-Dmaven.compiler.executable=" + javacWrapper.absolutePath
        ]
        def result = new ProcessExecutor()
                .redirectOutput(System.out)
                .redirectError(System.err)
                .environment(mvnEnvironment)
                .directory(repositoryDirectory)
                .command(mvnCommand).execute()
        if (result.getExitValue() != 0) {
            throw new KytheIndexException() //todo: fill in exception
        }

        kytheDir.listFiles(new FilenameFilter() {
            @Override
            boolean accept(File file, String s) {
                return s.endsWith(".kindex")
            }
        }).each {
            processKytheIndexFile(it)
        }
        return new KytheIndexExtractor(indexObservers).processIndexFile(new File("/tmp/stuff/done.txt"))
    }

    private static void processKytheIndexFile(File importFile) {
        def outputFile = new File("/tmp/stuff/done.txt")
        def indexCommand = [
                "/bin/sh",
                "-c",
                "java -Xbootclasspath/p:" + javaIndexer.absolutePath +
                        " com.google.devtools.kythe.analyzers.java.JavaIndexer " + importFile.absolutePath + " | " +
                        dedupStreamTool.absolutePath + " | " + triplesTool.absolutePath +
                        " >> " + outputFile.absolutePath
        ]

        def result = new ProcessExecutor()
                .redirectOutput(System.out)
                .redirectError(System.err)
                .command(indexCommand).execute()
        if (result.getExitValue() != 0) {
            throw new KytheIndexException() //todo: fill in exception
        }
    }
}
