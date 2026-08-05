package net.vanfek.proxybans.core.duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationParserTest {

    @Nested
    @DisplayName("unit letters")
    class Units {

        @ParameterizedTest(name = "{0} -> {1} seconds")
        @CsvSource({
                "30s, 30",
                "5m, 300",
                "2h, 7200",
                "1d, 86400",
                "1w, 604800",
                "1y, 31536000"
        })
        void parsesEachUnit(String input, long expectedSeconds) {
            assertThat(DurationParser.parse(input).duration()).isEqualTo(Duration.ofSeconds(expectedSeconds));
        }

        @Test
        @DisplayName("uppercase M is months, lowercase m is minutes")
        void distinguishesMonthsFromMinutes() {
            Duration months = DurationParser.parse("3M").duration();
            Duration minutes = DurationParser.parse("3m").duration();

            assertThat(months).isEqualTo(Duration.ofDays(90));
            assertThat(minutes).isEqualTo(Duration.ofMinutes(3));
            assertThat(months).isNotEqualTo(minutes);
        }

        @Test
        void combinesUnits() {
            assertThat(DurationParser.parse("7d12h30m").duration())
                    .isEqualTo(Duration.ofDays(7).plusHours(12).plusMinutes(30));
        }
    }

    @Nested
    @DisplayName("permanent tokens")
    class Permanent {

        @ParameterizedTest
        @ValueSource(strings = {"perm", "PERM", "permanent", "forever", "never", "0"})
        void areRecognisedRegardlessOfCase(String input) {
            assertThat(DurationParser.parse(input).isPermanent()).isTrue();
        }

        @Test
        void exposeNullDuration() {
            assertThat(DurationParser.parse("perm").duration()).isNull();
            assertThat(DurationParser.parse("perm").asOptional()).isEmpty();
        }
    }

    @Nested
    @DisplayName("rejections")
    class Rejections {

        @Test
        @DisplayName("a duration that would overflow a long is rejected, not silently negated")
        void rejectsOverflow() {
            assertThatThrownBy(() -> DurationParser.parse("999999999999y"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too large");
        }

        @Test
        void rejectsDurationsBeyondTheMaximum() {
            assertThatThrownBy(() -> DurationParser.parse("4000d"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maximum");
        }

        @Test
        @DisplayName("an explicit zero-length duration is an error, not an instantly expired ban")
        void rejectsZeroLength() {
            assertThatThrownBy(() -> DurationParser.parse("0s"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "1x", "d1", "1d!", "", "   "})
        void rejectsMalformedInput(String input) {
            assertThatThrownBy(() -> DurationParser.parse(input)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("tryParse")
    class TryParse {

        @Test
        @DisplayName("plain words are not durations, so /ban can treat them as the reason")
        void returnsEmptyForText() {
            assertThat(DurationParser.tryParse("cheating")).isEmpty();
            assertThat(DurationParser.tryParse("x-ray")).isEmpty();
        }

        @Test
        void returnsValueForDurations() {
            Optional<ParsedDuration> parsed = DurationParser.tryParse("2h");

            assertThat(parsed).isPresent();
            assertThat(parsed.orElseThrow().duration()).isEqualTo(Duration.ofHours(2));
        }
    }

}
