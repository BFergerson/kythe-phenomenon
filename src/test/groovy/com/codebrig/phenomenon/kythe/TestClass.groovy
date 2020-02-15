package com.codebrig.phenomenon.kythe

import com.codebrig.phenomena.Phenomena
import com.codebrig.phenomena.code.CodeObserver
import com.codebrig.phenomena.code.CodeObserverVisitor
import com.codebrig.phenomena.code.structure.CodeStructureObserver
import com.codebrig.phenomenon.kythe.build.KytheIndexBuilder
import com.codebrig.phenomenon.kythe.observe.KytheRefCallObserver
import org.eclipse.jgit.api.Git
import org.junit.Test

import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

class TestClass {

    @Test
    void myProject() {
        def outDir = new File("/tmp/stuff/myproject-master")
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
        def index = new KytheIndexBuilder(outDir).build(kytheObservers)

        def phenomena = new Phenomena()
        phenomena.scanPath = new ArrayList<>()
        phenomena.scanPath.add(outDir.absolutePath)
        def observers = new ArrayList<CodeObserver>()
        observers.add(new CodeStructureObserver())
        observers.addAll(kytheObservers)
        def visitor = new CodeObserverVisitor()
        visitor.addObservers(observers)
        phenomena.setupVisitor(visitor)
        phenomena.connectToBabelfish()
        phenomena.processScanPath().collect(Collectors.toList())
        phenomena.close()

        assertEquals(1, refCallObserver.functionCalls.size())
        assertNotNull(refCallObserver.functionCalls.get("java.io.PrintStream.println()"))
        assertEquals(16, refCallObserver.functionCalls.get("java.io.PrintStream.println()").size())
    }

    @Test
    void otherProject() {
        def outDir = new File("/tmp/stuff/otherproject-master")
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
        def index = new KytheIndexBuilder(outDir).build(kytheObservers)

        def phenomena = new Phenomena()
        phenomena.scanPath = new ArrayList<>()
        phenomena.scanPath.add(outDir.absolutePath)
        def observers = new ArrayList<CodeObserver>()
        observers.add(new CodeStructureObserver())
        observers.addAll(kytheObservers)
        def visitor = new CodeObserverVisitor()
        visitor.addObservers(observers)
        phenomena.setupVisitor(visitor)
        phenomena.connectToBabelfish()
        phenomena.processScanPath().collect(Collectors.toList())
        phenomena.close()

        assertEquals(6, refCallObserver.functionCalls.size())
        assertNotNull(refCallObserver.functionCalls.get("com.gitdetective.MyClass.myMethod()"))
        assertEquals(1, refCallObserver.functionCalls.get("com.gitdetective.MyClass.myMethod()").size())
        assertNotNull(refCallObserver.functionCalls.get("java.util.List.size()"))
        assertEquals(1, refCallObserver.functionCalls.get("java.util.List.size()").size())
        assertNotNull(refCallObserver.functionCalls.get("com.gitdetective.MyClass.myMethod2(java.lang.String)"))
        assertEquals(1, refCallObserver.functionCalls.get("com.gitdetective.MyClass.myMethod2(java.lang.String)").size())
        assertNotNull(refCallObserver.functionCalls.get("java.io.PrintStream.println()"))
        assertEquals(2, refCallObserver.functionCalls.get("java.io.PrintStream.println()").size())
        assertNotNull(refCallObserver.functionCalls.get("com.google.common.collect.Lists.newArrayList()"))
        assertEquals(1, refCallObserver.functionCalls.get("com.google.common.collect.Lists.newArrayList()").size())
        assertNotNull(refCallObserver.functionCalls.get("java.util.List.add()"))
        assertEquals(1, refCallObserver.functionCalls.get("java.util.List.add()").size())
    }
}
