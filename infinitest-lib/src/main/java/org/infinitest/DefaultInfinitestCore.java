/*
 * Infinitest, a Continuous Test Runner.
 *
 * Copyright (C) 2010-2013
 * "Ben Rady" <benrady@gmail.com>,
 * "Rod Coffin" <rfciii@gmail.com>,
 * "Ryan Breidenbach" <ryan.breidenbach@gmail.com>
 * "David Gageot" <david@gageot.net>, et al.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.infinitest;

import static com.google.common.collect.Lists.*;
import static com.google.common.collect.Sets.*;
import static java.util.logging.Level.*;
import static org.infinitest.util.InfinitestUtils.*;

import java.util.*;

import org.infinitest.parser.*;
import org.infinitest.testrunner.*;
import org.infinitest.testrunner.queue.*;

/**
 * @author <a href="mailto:benrady@gmail.com">Ben Rady</a>
 */
class DefaultInfinitestCore implements InfinitestCore {
	private final TestRunner runner;
	private TestDetector testDetector;
	private final Set<Class<? extends Throwable>> caughtExceptions;
	private final EventNormalizer normalizer;
	private RuntimeEnvironment currentEnvironment;
	private String name;
	private final List<ReloadListener> reloadListeners;
	private final List<DisabledTestListener> disabledTestListeners;
	private final RunStatistics stats;

	DefaultInfinitestCore(TestRunner testRunner, EventQueue eventQueue) {
		normalizer = new EventNormalizer(eventQueue);
		runner = testRunner;
		reloadListeners = newArrayList();
		caughtExceptions = newLinkedHashSet();
		disabledTestListeners = newArrayList();

		stats = new RunStatistics();
		runner.addTestResultsListener(stats);
		runner.setTestPriority(new TestComparator(stats));
	}

	public void setTestDetector(TestDetector testDetector) {
		this.testDetector = testDetector;
	}

	@Override
	public synchronized int update(Set<JavaClass> changedClasses) {
		if (changedClasses.isEmpty()) {
			return 0;
		}

		log(CONFIG, "Core Update " + name);
		int testsRun = runOptimizedTestSet(changedClasses);
		caughtExceptions.clear();
		return testsRun;
	}

	@Override
	public void reload() {
		Log.log("RELOAD " + name);
		log("Reloading core " + name);
		testDetector.clear();

		fireReload();
	}

	@Override
	public void setRuntimeEnvironment(RuntimeEnvironment environment) {
		if ((currentEnvironment == null) || !environment.equals(currentEnvironment)) {
			currentEnvironment = environment;
			runner.setRuntimeEnvironment(environment);
			testDetector.setClasspathProvider(environment);
			reload();
		}
	}

	private int runOptimizedTestSet(Set<JavaClass> changedClasses) {
		Set<String> oldTests = testDetector.getCurrentTests();
		Collection<JavaClass> testsToRun = testDetector.findTestsToRun(changedClasses);
		Set<String> newTests = testDetector.getCurrentTests();
		fireDisabledTestEvents(difference(oldTests, newTests));
		if (!testsToRun.isEmpty()) {
			log(name + " Running tests: " + testsToRun);
			runTests(testsToRun);
		}
		return testsToRun.size();
	}

	public RunStatistics getRunStatistics() {
		return stats;
	}

	@Override
	public void addTestResultsListener(TestResultsListener l) {
		getRunner().addTestResultsListener(normalizer.testEventNormalizer(l));
	}

	@Override
	public void removeTestResultsListener(TestResultsListener l) {
		getRunner().removeTestStatusListener(normalizer.testEventNormalizer(l));
	}

	@Override
	public RuntimeEnvironment getRuntimeEnvironment() {
		return currentEnvironment;
	}

	@Override
	public void addTestQueueListener(TestQueueListener listener) {
		getRunner().addTestQueueListener(normalizer.testQueueNormalizer(listener));
		reloadListeners.add(listener);
	}

	@Override
	public void removeTestQueueListener(TestQueueListener listener) {
		getRunner().removeTestQueueListener(normalizer.testQueueNormalizer(listener));
		reloadListeners.remove(listener);
	}

	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void addConsoleOutputListener(ConsoleOutputListener listener) {
		getRunner().addConsoleOutputListener(normalizer.consoleEventNormalizer(listener));
	}

	@Override
	public void removeConsoleOutputListener(ConsoleOutputListener listener) {
		getRunner().removeConsoleOutputListener(normalizer.consoleEventNormalizer(listener));
	}

	@Override
	public void addDisabledTestListener(DisabledTestListener listener) {
		disabledTestListeners.add(listener);
	}

	@Override
	public void removeDisabledTestListener(DisabledTestListener listener) {
		disabledTestListeners.remove(listener);
	}

	@Override
	public boolean isEventSourceFor(TestCaseEvent testCaseEvent) {
		return testCaseEvent.getSource().equals(getRunner());
	}

	TestRunner getRunner() {
		return runner;
	}

	private void fireDisabledTestEvents(Set<String> disabledTests) {
		for (DisabledTestListener each : disabledTestListeners) {
			each.testsDisabled(disabledTests);
		}
	}

	private void checkForFatalError(Throwable e) {
		if (caughtExceptions.contains(e.getClass())) {
			log("Fatal error occured while updating Error while updating", e);
			throw new FatalInfinitestError("Error running tests", e);
		}
		Log.log(name + " Error while updating" + e);
		log(name + " Error while updating", e);
		reload();
		caughtExceptions.add(e.getClass());
	}

	private void runTests(Collection<JavaClass> testsToRun) {
		List<String> tests = classesToNames(testsToRun);
		getRunner().runTests(tests);
	}

	private List<String> classesToNames(Collection<JavaClass> classes) {
		List<String> tests = newArrayList();
		for (JavaClass javaClass : classes) {
			tests.add(javaClass.getName());
		}
		return tests;
	}

	private void fireReload() {
		for (ReloadListener each : reloadListeners) {
			each.reloading();
		}
	}
}
