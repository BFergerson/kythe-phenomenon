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

    static final String javacWrapperLocation = "extractors/javac-wrapper.sh"
    static final String javacExtractorLocation = "extractors/javac_extractor.jar"
    static final String javaIndexerLocation = "indexers/java_indexer.jar"
    static final String dedupStreamToolLocation = "tools/dedup_stream"
    static final String triplesToolLocation = "tools/triples"
    private final File repositoryDirectory
    private File kytheDirectory = new File("opt/kythe-v0.0.28")
    private File kytheOutputDirectory = new File((System.getProperty("os.name").toLowerCase().startsWith("mac"))
            ? "/tmp" : System.getProperty("java.io.tmpdir"), "kythe-phenomenon")

    KytheIndexBuilder(File repositoryDirectory) {
        this.repositoryDirectory = Objects.requireNonNull(repositoryDirectory)
    }

    KytheIndexBuilder setKytheDirectory(File kytheDirectory) {
        this.kytheDirectory = Objects.requireNonNull(kytheDirectory)
        return this
    }

    File getKytheDirectory() {
        return kytheDirectory
    }

    KytheIndexBuilder setKytheOutputDirectory(File kytheOutputDirectory) {
        this.kytheOutputDirectory = Objects.requireNonNull(kytheOutputDirectory)
        return this
    }

    File getKytheOutputDirectory() {
        return kytheOutputDirectory
    }

    KytheIndex build(List<KytheIndexObserver> indexObservers) throws KytheIndexException {
        kytheOutputDirectory.mkdirs()

        def mvnEnvironment = [
                REAL_JAVAC            : "/usr/bin/javac",
                KYTHE_ROOT_DIRECTORY  : repositoryDirectory.absolutePath,
                KYTHE_OUTPUT_DIRECTORY: kytheOutputDirectory.absolutePath,
                JAVAC_EXTRACTOR_JAR   : new File(kytheDirectory, javacExtractorLocation).absolutePath
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
                "-Dmaven.compiler.executable=" + new File(kytheDirectory, javacWrapperLocation).absolutePath
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

        kytheOutputDirectory.listFiles(new FilenameFilter() {
            @Override
            boolean accept(File file, String s) {
                return s.endsWith(".kindex")
            }
        }).each {
            processKytheIndexFile(it)
        }

        def kytheIndex = new KytheIndexExtractor(kytheDirectory, indexObservers)
                .processIndexFile(new File(kytheOutputDirectory, "kythe_phenomenon_triples"))
        indexObservers.each {
            it.setKytheIndex(kytheIndex)
        }
        return kytheIndex
    }

    private void processKytheIndexFile(File importFile) {
        def outputFile = new File(kytheOutputDirectory, "kythe_phenomenon_triples")
        def indexCommand = [
                "/bin/sh",
                "-c",
                "java -Xbootclasspath/p:" + new File(kytheDirectory, javaIndexerLocation).absolutePath +
                        " com.google.devtools.kythe.analyzers.java.JavaIndexer " + importFile.absolutePath + " | " +
                        new File(kytheDirectory, dedupStreamToolLocation).absolutePath + " | " +
                        new File(kytheDirectory, triplesToolLocation).absolutePath +
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
