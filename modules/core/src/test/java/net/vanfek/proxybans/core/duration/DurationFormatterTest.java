package net.vanfek.proxybans.core.duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DurationFormatterTest {

    @ParameterizedTest(name = "{0}s -> {1}")
    @CsvSource({
            "45, 45с",
            "60, 1м",
            "3600, 1ч",
            "86400, 1д",
            "90061, '1д 1ч 1м 1с'"
    })
    void rendersCoarsestUnits(long seconds, String expected) {
        assertThat(DurationFormatter.human(Duration.ofSeconds(seconds))).isEqualTo(expected);
    }

    @Test
    @DisplayName("a ban that already expired renders as 0s rather than a negative duration")
    void clampsNegativeDurations() {
        assertThat(DurationFormatter.human(Duration.ofSeconds(-500))).isEqualTo("0с");
    }

    @Test
    void rendersZeroExplicitly() {
        assertThat(DurationFormatter.human(Duration.ZERO)).isEqualTo("0с");
    }

}
