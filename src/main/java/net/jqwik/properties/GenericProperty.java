package net.jqwik.properties;

import org.junit.platform.commons.util.*;
import org.opentest4j.*;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class GenericProperty {

	private final String name;
	private final List<Arbitrary> arbitraries;
	private final Function<List<Object>, Boolean> forAllFunction;

	public GenericProperty(String name, List<Arbitrary> arbitraries, CheckedFunction forAllFunction) {
		this.name = name;
		this.arbitraries = arbitraries;
		this.forAllFunction = forAllFunction;
	}

	public PropertyCheckResult check(int tries, long seed) {
		Random random = new Random(seed);
		List<RandomGenerator> generators = arbitraries.stream().map(a1 -> a1.generator(tries)).collect(Collectors.toList());
		int maxTries = generators.isEmpty() ? 1 : tries;
		int countChecks = 0;
		for (int countTries = 1; countTries <= maxTries; countTries++) {
			List<Object> params = generateParameters(generators, random);
			try {
				boolean check = forAllFunction.apply(params);
				countChecks++;
				if (!check) {
					return PropertyCheckResult.falsified(name, countTries, countChecks, seed, params, null);
				}
			} catch (AssertionError ae) {
				countChecks++;
				return PropertyCheckResult.falsified(name, countTries, countChecks, seed, params, ae);
			} catch (TestAbortedException tae) {
				continue;
			} catch (Throwable throwable) {
				countChecks++;
				BlacklistedExceptions.rethrowIfBlacklisted(throwable);
				return PropertyCheckResult.erroneous(name, countTries, countChecks, seed, params, throwable);
			}
		}
		if (countChecks == 0)
			return PropertyCheckResult.exhausted(name, maxTries, seed);
		return PropertyCheckResult.satisfied(name, maxTries, countChecks, seed);
	}

	private List<Object> generateParameters(List<RandomGenerator> generators, Random random) {
		return generators.stream().map(generator -> generator.next(random)).collect(Collectors.toList());
	}
}
