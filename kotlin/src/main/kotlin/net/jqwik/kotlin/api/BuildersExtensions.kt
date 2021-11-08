package net.jqwik.kotlin.api

import net.jqwik.api.Arbitrary
import net.jqwik.api.Builders.BuilderCombinator
import org.apiguardian.api.API

/**
 * Convenience function for Kotlin to not use backticked `in` function.
 */
@API(status = API.Status.EXPERIMENTAL, since = "1.6.0")
fun <B, T> BuilderCombinator<B>.use(arbitrary: Arbitrary<T>, combinator: (B, T) -> B): BuilderCombinator<B> {
    return this.use(arbitrary).`in`(combinator)
}
