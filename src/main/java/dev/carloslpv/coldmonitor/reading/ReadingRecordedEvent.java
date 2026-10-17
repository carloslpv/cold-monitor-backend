package dev.carloslpv.coldmonitor.reading;

/**
 * Published for every stored reading, in range or not. The live reading stream listens to it.
 */
public record ReadingRecordedEvent(ReadingResponse reading) {

	static ReadingRecordedEvent from(TemperatureReading reading) {
		return new ReadingRecordedEvent(ReadingResponse.from(reading));
	}

}
