package com.gamemasterx.server.dice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Owns the lifecycle of the shared {@link DiceRoller} bean.
 *
 * <p>{@link DiceRoller} is a plain, stateless utility. It is built fresh per
 * call by {@code InitiativeService} and by {@link
 * com.gamemasterx.server.dice.controller.DiceController}, which also injects a
 * single shared instance for its field. That shared singleton is created here
 * with {@link RollMode#RANDOM} and no seed &ndash; the only combination that
 * makes sense for a process-wide instance. Keeping this in one small
 * configuration is the single place that wires the two dependencies
 * {@link DiceRoller}'s constructor requires.</p>
 */
@Configuration(proxyBeanMethods = false)
public class DiceConfig {

    /**
     * The process-wide, shared roller. Defaults to random, unseeded rolls.
     */
    @Bean
    @ConditionalOnMissingBean(DiceRoller.class)
    public DiceRoller diceRoller() {
        return new DiceRoller(RollMode.RANDOM, null);
    }
}
