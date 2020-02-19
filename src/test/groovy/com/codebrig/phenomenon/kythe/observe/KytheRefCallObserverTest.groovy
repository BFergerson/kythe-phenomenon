package com.codebrig.phenomenon.kythe.observe

import com.codebrig.phenomena.Phenomena
import com.codebrig.phenomena.code.CodeObserverVisitor
import com.codebrig.phenomenon.kythe.KytheIndexObserver
import com.codebrig.phenomenon.kythe.build.KytheIndexBuilder
import org.eclipse.jgit.api.Git
import org.junit.Test

import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static org.junit.Assert.*

class KytheRefCallObserverTest {

    @Test
    void myProject() {
        def outDir = new File("/tmp/stuff")
        if (outDir.exists()) {
            outDir.deleteDir()
        }

        def githubRepository = "bfergerson/myproject"
        Git.cloneRepository()
                .setURI("https://github.com/" + githubRepository + ".git")
                .setDirectory(outDir)
                .setCloneSubmodules(true)
                .setTimeout(TimeUnit.MINUTES.toSeconds(5) as int)
                .call()

        def kytheObservers = new ArrayList<KytheIndexObserver>()
        kytheObservers.add(new KytheRefCallObserver())
        def index = new KytheIndexBuilder(outDir)
                .setKytheOutputDirectory(outDir)
                .build(kytheObservers)

        def phenomena = new Phenomena()
        phenomena.scanPath = new ArrayList<>()
        phenomena.scanPath.add(outDir.absolutePath)
        def visitor = new CodeObserverVisitor()
        visitor.addObservers(kytheObservers)
        phenomena.setupVisitor(visitor)
        phenomena.connectToBabelfish()
        phenomena.processScanPath().collect(Collectors.toList())
        phenomena.close()

        def observedNodes = visitor.getObservedContextualNodes()
        assertEquals(10, observedNodes.size())
        assertEquals(10, observedNodes.findAll { it.internalType == "MethodDeclaration" }.size())
        assertEquals(0, observedNodes.findAll { it.internalType == "MethodInvocation" }.size())
    }

    @Test
    void otherProject() {
        def outDir = new File("/tmp/stuff")
        if (outDir.exists()) {
            outDir.deleteDir()
        }

        def githubRepository = "bfergerson/otherproject"
        Git.cloneRepository()
                .setURI("https://github.com/" + githubRepository + ".git")
                .setDirectory(outDir)
                .setCloneSubmodules(true)
                .setTimeout(TimeUnit.MINUTES.toSeconds(5) as int)
                .call()

        def kytheObservers = new ArrayList<KytheIndexObserver>()
        kytheObservers.add(new KytheRefCallObserver())
        def index = new KytheIndexBuilder(outDir)
                .setKytheOutputDirectory(outDir)
                .build(kytheObservers)

        def phenomena = new Phenomena()
        phenomena.scanPath = new ArrayList<>()
        phenomena.scanPath.add(outDir.absolutePath)
        def visitor = new CodeObserverVisitor()
        visitor.addObservers(kytheObservers)
        phenomena.setupVisitor(visitor)
        phenomena.connectToBabelfish()
        phenomena.processScanPath().collect(Collectors.toList())
        phenomena.close()

        def observedNodes = visitor.getObservedContextualNodes()
        assertEquals(5, observedNodes.size())
        assertEquals(2, observedNodes.findAll { it.internalType == "MethodDeclaration" }.size())
        assertEquals(3, observedNodes.findAll { it.internalType == "MethodInvocation" }.size())

        def mainMethodReferences = observedNodes.findAll {
            it.internalType == "MethodInvocation" &&
                    it.parentSourceNode.name == "com.gitdetective.App2.main(java.lang.String[])"
        }.toList()
        assertNotNull(mainMethodReferences)
        assertEquals(2, mainMethodReferences.size())
        assertEquals(2, mainMethodReferences.get(0).attributes.size())
        assertTrue(["com.gitdetective.MyClass.myMethod()", "com.google.common.collect.Lists.newArrayList()"].contains(
                mainMethodReferences.get(0).attributes.get("calledQualifiedName")))
        assertTrue(["com.gitdetective.MyClass.myMethod()", "com.google.common.collect.Lists.newArrayList()"].contains(
                mainMethodReferences.get(1).attributes.get("calledQualifiedName")))

        def anotherOneMethodReferences = observedNodes.findAll {
            it.internalType == "MethodInvocation" &&
                    it.parentSourceNode.name == "com.gitdetective.App2.anotherOne(java.lang.String,int)"
        }.toList()
        assertNotNull(anotherOneMethodReferences)
        assertEquals(1, anotherOneMethodReferences.size())
        assertEquals(2, anotherOneMethodReferences.get(0).attributes.size())
        assertEquals("com.gitdetective.MyClass.myMethod2()",
                anotherOneMethodReferences.get(0).attributes.get("calledQualifiedName"))
    }
}
