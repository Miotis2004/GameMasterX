package com.gamemasterx.server.narration.config;

import com.gamemasterx.server.narration.service.NarrationContextAssembler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the AI narration context-assembly bounded context.
 *
 * <p>Registers the {@link NarrationContextProperties} bound from
 * {@code game.master.x.narration} and the {@link NarrationContextAssembler} that
 * owns the assembly logic. The assembler depends on the standard domain
 * repositories and services of the other bounded contexts (encounter, campaign,
 * adventure, character, membership), which are already contributed by their own
 * auto-configurations, so this configuration only needs to supply these two
 * beans.</p>
 *
 * <p>All values are safe to leave unset: {@link NarrationContextProperties}
 * carries sensible defaults that keep the assembled context within the AI
 * provider's context window and gated behind the {@code PLAYER} role by
 * default.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NarrationContextProperties.class)
public class NarrationAutoConfiguration {

    /**
     * Registers the context assembler. Constructed from the resolved domain
     * repositories and services; it is the single entry point the narration
     * controller uses to assemble an AI narration context.
     */
    @Bean
    public NarrationContextAssembler narrationContextAssembler(
            com.gamemasterx.server.encounter.repository.EncounterRepository encounterRepository,
            com.gamemasterx.server.campaign.repository.CampaignRepository campaignRepository,
            com.gamemasterx.server.adventure.repository.AdventureRepository adventureRepository,
            com.gamemasterx.server.character.repository.CharacterRepository characterRepository,
            com.gamemasterx.server.gameplay.repository.TurnRepository turnRepository,
            com.gamemasterx.server.campaign.membership.service.MembershipService membershipService,
            NarrationContextProperties properties) {
        return new NarrationContextAssembler(
                encounterRepository, campaignRepository, adventureRepository,
                characterRepository, turnRepository, membershipService, properties);
    }
}
