package dev.carloslpv.coldmonitor.reading;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingStatusTests {

	@ParameterizedTest
	@CsvSource({
			"1.99, BELOW_MIN",
			"2.00, IN_RANGE",
			"5.00, IN_RANGE",
			"8.00, IN_RANGE",
			"8.01, ABOVE_MAX",
			"-30.00, BELOW_MIN",
			"25.00, ABOVE_MAX" })
	void classifiesAgainstInclusiveLimits(BigDecimal temperature, ReadingStatus expected) {
		var status = ReadingStatus.classify(temperature, new BigDecimal("2.00"), new BigDecimal("8.00"));

		assertThat(status).isEqualTo(expected);
		assertThat(status.isOutOfRange()).isEqualTo(expected != ReadingStatus.IN_RANGE);
	}

	@ParameterizedTest
	@CsvSource({ "-25.01, BELOW_MIN", "-25, IN_RANGE", "-18.5, IN_RANGE", "-15, IN_RANGE", "-14.99, ABOVE_MAX" })
	void ignoresScaleDifferences(BigDecimal temperature, ReadingStatus expected) {
		assertThat(ReadingStatus.classify(temperature, new BigDecimal("-25.00"), new BigDecimal("-15.00")))
			.isEqualTo(expected);
	}

}
