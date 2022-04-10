package net.jqwik.engine.properties.state;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

import org.jetbrains.annotations.*;
import org.opentest4j.*;

import net.jqwik.api.*;
import net.jqwik.api.Tuple.*;
import net.jqwik.api.state.*;
import net.jqwik.engine.*;
import net.jqwik.engine.properties.*;

public class ShrinkableChain<T> implements Shrinkable<Chain<T>> {

	private final long randomSeed;
	private final Supplier<? extends T> initialSupplier;
	private final Function<Random, TransformerProvider<T>> providerGenerator;
	private final int maxTransformations;
	private final int genSize;
	private final List<ShrinkableChainIteration<T>> iterations;
	private final Supplier<ChangeDetector<T>> changeDetectorSupplier;

	public ShrinkableChain(
		long randomSeed,
		Supplier<? extends T> initialSupplier,
		Function<Random, TransformerProvider<T>> providerGenerator,
		Supplier<ChangeDetector<T>> changeDetectorSupplier,
		int maxTransformations,
		int genSize
	) {
		this(randomSeed, initialSupplier, providerGenerator, changeDetectorSupplier, maxTransformations, genSize, new ArrayList<>());
	}

	private ShrinkableChain(
		long randomSeed, Supplier<? extends T> initialSupplier,
		Function<Random, TransformerProvider<T>> providerGenerator,
		Supplier<ChangeDetector<T>> changeDetectorSupplier,
		int maxTransformations,
		int genSize,
		List<ShrinkableChainIteration<T>> iterations
	) {
		this.randomSeed = randomSeed;
		this.initialSupplier = initialSupplier;
		this.providerGenerator = providerGenerator;
		this.changeDetectorSupplier = changeDetectorSupplier;
		this.maxTransformations = maxTransformations;
		this.genSize = genSize;
		this.iterations = iterations;
	}

	@Override
	@NotNull
	public Chain<T> value() {
		return new ChainInstance();
	}

	@Override
	@NotNull
	public Stream<Shrinkable<Chain<T>>> shrink() {
		return new ShrinkableChainShrinker_NEW<>(this, iterations, maxTransformations).shrink();
		// return new ShrinkableChainShrinker<>(this, iterations, maxTransformations).shrink();
	}

	ShrinkableChain<T> cloneWith(List<ShrinkableChainIteration<T>> shrunkIterations, int newMaxSize) {
		return new ShrinkableChain<>(
			randomSeed,
			initialSupplier,
			providerGenerator,
			changeDetectorSupplier,
			newMaxSize,
			genSize,
			shrunkIterations
		);
	}

	@Override
	@NotNull
	public ShrinkingDistance distance() {
		List<Shrinkable<Transformer<T>>> shrinkablesForDistance = new ArrayList<>();
		for (int i = 0; i < maxTransformations; i++) {
			if (i < iterations.size()) {
				shrinkablesForDistance.add(iterations.get(i).shrinkable);
			} else {
				shrinkablesForDistance.add(Shrinkable.unshrinkable(t -> t));
			}
		}
		return ShrinkingDistance.forCollection(shrinkablesForDistance);
	}

	@Override
	public String toString() {
		return String.format("ShrinkableChain[maxSize=%s, iterations=%s]", maxTransformations, iterations);
	}

	private class ChainInstance implements Chain<T> {

		@Override
		@NotNull
		public Iterator<T> start() {
			return new ChainIterator(initialSupplier.get());
		}

		@Override
		public int maxTransformations() {
			return maxTransformations;
		}

		@Override
		@NotNull
		public List<String> transformations() {
			return iterations.stream().map(i -> i.shrinkable.value().transformation()).collect(Collectors.toList());
		}
	}

	private class ChainIterator implements Iterator<T> {

		private final Random random = SourceOfRandomness.newRandom(randomSeed);
		private int steps = 0;
		private T current;
		private boolean initialSupplied = false;
		private Transformer<T> nextTransformer = null;

		private ChainIterator(T initial) {
			this.current = initial;
		}

		@Override
		public synchronized boolean hasNext() {
			if (!initialSupplied) {
				return true;
			}
			if (isInfinite()) {
				nextTransformer = nextTransformer();
				return !nextTransformer.isEndOfChain();
			} else {
				if (steps < maxTransformations) {
					nextTransformer = nextTransformer();
					return !nextTransformer.isEndOfChain();
				} else {
					return false;
				}
			}
		}

		@Override
		public T next() {
			if (!initialSupplied) {
				initialSupplied = true;
				return current;
			}

			// Create deterministic random in order to reuse in shrinking
			long nextSeed = random.nextLong();
			Transformer<T> transformer = null;

			synchronized (ShrinkableChain.this) {
				transformer = nextTransformer;
				current = transformState(transformer, current);
				return current;
			}
		}

		private Transformer<T> nextTransformer() {
			// Create deterministic random in order to reuse in shrinking
			long nextSeed = random.nextLong();

			Shrinkable<Transformer<T>> next = null;
			if (steps < iterations.size()) {
				next = rerunStep(nextSeed);
			} else {
				next = runNewStep(nextSeed);
			}
			return next.value();
		}

		private T transformState(Transformer<T> transformer, T before) {
			ChangeDetector<T> changeDetector = changeDetectorSupplier.get();
			changeDetector.before(before);
			try {
				T after = transformer.apply(before);
				boolean stateHasChanged = changeDetector.hasChanged(after);
				ShrinkableChainIteration<T> currentIteration = iterations.get(steps);
				iterations.set(steps, currentIteration.withStateChange(stateHasChanged));
				return after;
			} finally {
				steps++;
			}
		}

		private Shrinkable<Transformer<T>> rerunStep(long nextSeed) {
			ShrinkableChainIteration<T> iteration = iterations.get(steps);
			iteration.precondition().ifPresent(predicate -> {
				if (!predicate.test(current)) {
					throw new TestAbortedException("Precondition no longer valid");
				}
			});
			return iteration.shrinkable;
		}

		private Shrinkable<Transformer<T>> runNewStep(long nextSeed) {
			Random random = SourceOfRandomness.newRandom(nextSeed);
			Tuple4<Arbitrary<Transformer<T>>, Boolean, Predicate<T>, Boolean> arbitraryAccessTuple = nextTransformerArbitrary(random);
			Arbitrary<Transformer<T>> arbitrary = arbitraryAccessTuple.get1();
			boolean stateHasBeenAccessed = arbitraryAccessTuple.get2();
			Predicate<T> precondition = arbitraryAccessTuple.get3();
			boolean accessState = arbitraryAccessTuple.get4();

			RandomGenerator<Transformer<T>> generator = arbitrary.generator(genSize);
			Shrinkable<Transformer<T>> next = generator.next(random);
			iterations.add(new ShrinkableChainIteration<>(nextSeed, stateHasBeenAccessed, precondition, accessState, next));
			return next;
		}

		private Tuple4<Arbitrary<Transformer<T>>, Boolean, Predicate<T>, Boolean> nextTransformerArbitrary(Random random) {
			return MaxTriesLoop.loop(
				() -> true,
				arbitraryAccessTuple -> {
					TransformerProvider<T> chainGenerator = providerGenerator.apply(random);
					AtomicBoolean accessState = new AtomicBoolean(false);
					Supplier<T> supplier = () -> {
						accessState.set(true);
						return current;
					};

					Predicate<T> precondition = chainGenerator.precondition();
					boolean hasPrecondition = precondition != TransformerProvider.NO_PRECONDITION;
					if (hasPrecondition) {
						if (!precondition.test(current)) {
							return Tuple.of(false, null);
						}
					}

					Arbitrary<Transformer<T>> arbitrary = chainGenerator.apply(supplier);
					boolean stateHasBeenAccessed_OLD = accessState.get() || hasPrecondition;
					return Tuple.of(
						true,
						Tuple.of(arbitrary, stateHasBeenAccessed_OLD, hasPrecondition ? precondition : null, accessState.get())
					);
				},
				maxMisses -> {
					String message = String.format("Could not generate a transformer after %s tries.", maxMisses);
					return new JqwikException(message);
				},
				1000
			);
		}
	}

	private boolean isInfinite() {
		return maxTransformations < 0;
	}

}
