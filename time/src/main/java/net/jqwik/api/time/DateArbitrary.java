package net.jqwik.api.time;

import java.time.*;

import org.apiguardian.api.*;

import net.jqwik.api.*;

import static org.apiguardian.api.API.Status.*;

/**
 * Fluent interface to configure the generation of local date values.
 */
@API(status = EXPERIMENTAL, since = "1.4.0")
public interface DateArbitrary extends Arbitrary<LocalDate> {

	/**
	 * Set the allowed lower {@code min} (included) and upper {@code max} (included) bounder of generated local date values.
	 */
	default DateArbitrary between(LocalDate min, LocalDate max) {
		return atTheEarliest(min).atTheLatest(max);
	}

	/**
	 * Set the allowed lower {@code min} (included) bounder of generated local date values.
	 */
	DateArbitrary atTheEarliest(LocalDate min);

	/**
	 * Set the allowed upper {@code max} (included) bounder of generated local values.
	 */
	DateArbitrary atTheLatest(LocalDate max);

	/**
	 * Set the allowed lower {@code min} (included) and upper {@code max} (included) bounder of generated year values.
	 */
	DateArbitrary yearBetween(Year min, Year max);

	/**
	 * Set the allowed lower {@code min} (included) and upper {@code max} (included) bounder of generated year values.
	 */
	default DateArbitrary yearBetween(int min, int max) {
		return yearBetween(Year.of(min), Year.of(max));
	}

	/**
	 * Set the allowed lower {@code min} (included) and upper {@code max} (included) bounder of generated month values.
	 */
	DateArbitrary monthBetween(Month min, Month max);

	/**
	 * Set the allowed lower {@code min} (included) and upper {@code max} (included) bounder of generated month values.
	 */
	default DateArbitrary monthBetween(int min, int max) {
		return monthBetween(Month.of(min), Month.of(max));
	}

	/**
	 * Set an array of allowed {@code months}.
	 */
	DateArbitrary onlyMonths(Month... months);

	/**
	 * Set the allowed lower {@code min} (included) and upper {@code max} (included) bounder of generated day of month values.
	 */
	DateArbitrary dayOfMonthBetween(int min, int max);

	/**
	 * Set an array of allowed {@code daysOfWeek}.
	 */
	DateArbitrary onlyDaysOfWeek(DayOfWeek... daysOfWeek);

}
