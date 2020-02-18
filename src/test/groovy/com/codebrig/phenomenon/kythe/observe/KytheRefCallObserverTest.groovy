package com.codebrig.phenomenon.kythe.observe

import com.codebrig.arthur.observe.structure.filter.TypeFilter
import com.codebrig.phenomena.Phenomena
import com.codebrig.phenomena.code.CodeObserverVisitor
import com.codebrig.phenomenon.kythe.KytheIndexObserver
import com.codebrig.phenomenon.kythe.build.KytheIndexBuilder
import org.eclipse.jgit.api.Git
import org.junit.Test

import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

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
        def refCallObserver = new KytheRefCallObserver()
        kytheObservers.add(refCallObserver)
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

        assertEquals(0, refCallObserver.functionCalls.size())
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
        def refCallObserver = new KytheRefCallObserver()
        kytheObservers.add(refCallObserver)
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

        assertEquals(3, refCallObserver.functionCalls.size())
        def myMethodCallers = refCallObserver.functionCalls.get("com.gitdetective.MyClass.myMethod()")
        assertNotNull(myMethodCallers)
        assertEquals(1, myMethodCallers.size())
        assertEquals("com.gitdetective.App2.main(java.lang.String[])",
                new TypeFilter("MethodDeclaration")
                        .getFilteredNodes(myMethodCallers.get(0), false).next().name)

        def myMethod2Callers = refCallObserver.functionCalls.get("com.gitdetective.MyClass.myMethod2()")
        assertNotNull(myMethod2Callers)
        assertEquals(1, myMethod2Callers.size())
        assertEquals("com.gitdetective.App2.anotherOne(java.lang.String,int)",
                new TypeFilter("MethodDeclaration")
                        .getFilteredNodes(myMethod2Callers.get(0), false).next().name)

        def newArrayListCallers = refCallObserver.functionCalls.get("com.google.common.collect.Lists.newArrayList()")
        assertNotNull(newArrayListCallers)
        assertEquals("com.gitdetective.App2.main(java.lang.String[])",
                new TypeFilter("MethodDeclaration")
                        .getFilteredNodes(newArrayListCallers.get(0), false).next().name)
    }
}
