package net.vanfek.proxybans.core.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PunishmentArgumentsTest {

    @Test
    @DisplayName("a leading duration is taken as the duration and the rest as the reason")
    void splitsDurationFromReason() {
        PunishmentArguments arguments = PunishmentArguments.parse("7d griefing spawn");

        assertThat(arguments.duration().duration()).isEqualTo(Duration.ofDays(7));
        assertThat(arguments.reason()).isEqualTo("griefing spawn");
    }

    @Test
    @DisplayName("a multi-word reason whose first word is not a duration stays intact")
    void keepsWholeReasonWhenFirstWordIsNotADuration() {
        PunishmentArguments arguments = PunishmentArguments.parse("cheating with hacks");

        assertThat(arguments.duration().isPermanent()).isTrue();
        assertThat(arguments.reason()).isEqualTo("cheating with hacks");
    }

    @Test
    void singleWordReasonBecomesPermanentPunishment() {
        PunishmentArguments arguments = PunishmentArguments.parse("griefing");

        assertThat(arguments.duration().isPermanent()).isTrue();
        assertThat(arguments.reason()).isEqualTo("griefing");
    }

    @Test
    @DisplayName("an explicit permanent token is consumed, not treated as the reason")
    void consumesExplicitPermanentToken() {
        PunishmentArguments arguments = PunishmentArguments.parse("perm ban evasion");

        assertThat(arguments.duration().isPermanent()).isTrue();
        assertThat(arguments.reason()).isEqualTo("ban evasion");
    }

    @Test
    void reportsMissingReason() {
        assertThat(PunishmentArguments.parse("7d").hasReason()).isFalse();
        assertThat(PunishmentArguments.parse("7d griefing").hasReason()).isTrue();
    }

    @Test
    @DisplayName("uppercase M keeps meaning months here as well")
    void monthsSurviveTheSplit() {
        PunishmentArguments arguments = PunishmentArguments.parse("3M ban evasion");

        assertThat(arguments.duration().duration()).isEqualTo(Duration.ofDays(90));
        assertThat(arguments.reason()).isEqualTo("ban evasion");
    }

}
