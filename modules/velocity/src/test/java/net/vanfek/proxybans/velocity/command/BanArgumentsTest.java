package net.vanfek.proxybans.velocity.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the {@code /ban} argument split.
 *
 * <p>The failure this guards against is subtle: with a word-typed duration argument,
 * {@code /ban Steve cheating with hacks} used to answer "invalid duration: cheating" and ban nobody.
 */
class BanArgumentsTest {

    @Test
    @DisplayName("a leading duration is taken as the duration and the rest as the reason")
    void splitsDurationFromReason() {
        BanArguments arguments = BanArguments.parse("7d griefing spawn");

        assertThat(arguments.duration().duration()).isEqualTo(Duration.ofDays(7));
        assertThat(arguments.reason()).isEqualTo("griefing spawn");
    }

    @Test
    @DisplayName("a multi-word reason whose first word is not a duration stays intact")
    void keepsWholeReasonWhenFirstWordIsNotADuration() {
        BanArguments arguments = BanArguments.parse("cheating with hacks");

        assertThat(arguments.duration().isPermanent()).isTrue();
        assertThat(arguments.reason()).isEqualTo("cheating with hacks");
    }

    @Test
    void singleWordReasonBecomesPermanentBan() {
        BanArguments arguments = BanArguments.parse("griefing");

        assertThat(arguments.duration().isPermanent()).isTrue();
        assertThat(arguments.reason()).isEqualTo("griefing");
    }

    @Test
    @DisplayName("an explicit permanent token is consumed, not treated as the reason")
    void consumesExplicitPermanentToken() {
        BanArguments arguments = BanArguments.parse("perm ban evasion");

        assertThat(arguments.duration().isPermanent()).isTrue();
        assertThat(arguments.reason()).isEqualTo("ban evasion");
    }

    @Test
    void reportsMissingReason() {
        assertThat(BanArguments.parse("7d").hasReason()).isFalse();
        assertThat(BanArguments.parse("7d griefing").hasReason()).isTrue();
    }

    @Test
    @DisplayName("uppercase M keeps meaning months here as well")
    void monthsSurviveTheSplit() {
        BanArguments arguments = BanArguments.parse("3M ban evasion");

        assertThat(arguments.duration().duration()).isEqualTo(Duration.ofDays(90));
        assertThat(arguments.reason()).isEqualTo("ban evasion");
    }

}
