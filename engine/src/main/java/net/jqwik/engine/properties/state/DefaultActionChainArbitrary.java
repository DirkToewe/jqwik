package net.jqwik.engine.properties.state;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.jetbrains.annotations.*;

import net.jqwik.api.*;
import net.jqwik.api.Tuple.*;
import net.jqwik.api.arbitraries.*;
import net.jqwik.api.state.*;

public class DefaultActionChainArbitrary<T> extends ArbitraryDecorator<ActionChain<T>> implements ActionChainArbitrary<T> {

	private ChainArbitrary<T> chainArbitrary;

	public DefaultActionChainArbitrary(
		Supplier<? extends T> initialSupplier,
		List<Tuple2<Integer, Arbitrary<? extends Action<T>>>> actionArbitraryFrequencies
	) {
		List<Tuple2<Integer, TransformerProvider<T>>> providerFrequencies = toProviderFrequencies(actionArbitraryFrequencies);
		chainArbitrary = new DefaultChainArbitrary<>(initialSupplier, providerFrequencies);
	}

	private List<Tuple2<Integer, TransformerProvider<T>>> toProviderFrequencies(List<Tuple2<Integer, Arbitrary<? extends Action<T>>>> actionFrequencies) {
		return actionFrequencies
			.stream()
			.map(frequency -> {
				Arbitrary<? extends Action<T>> actionArbitrary = frequency.get2();
				TransformerProvider<T> provider = ignoreSupplier -> actionArbitrary.map(action -> {
					// TODO: handle preconditions and Action.provideTransformer and action.toString()
					Transformer<T> transformer = new Transformer<T>() {
						@Override
						public @NotNull T apply(@NotNull T state) {
							return action.run(state);
						}

						@Override
						public String toString() {
							return action.toString();
						}
					};
					return transformer;
				});
				return Tuple.of(frequency.get1(), provider);
			}).collect(Collectors.toList());
	}

	@Override
	@NotNull
	public ActionChainArbitrary<T> withMaxActions(int maxSize) {
		DefaultActionChainArbitrary<T> clone = typedClone();
		clone.chainArbitrary = clone.chainArbitrary.withMaxTransformations(maxSize);
		return clone;
	}

	@Override
	@NotNull
	protected Arbitrary<ActionChain<T>> arbitrary() {
		return chainArbitrary.map(SequentialActionChain::new);
	}
}