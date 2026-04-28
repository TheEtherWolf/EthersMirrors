package com.ether.mirrors.advancement;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nullable;

public class MirrorUpgradedTrigger extends SimpleCriterionTrigger<MirrorUpgradedTrigger.TriggerInstance> {

    public static final ResourceLocation ID = new ResourceLocation("ethersmirrors", "mirror_upgraded");

    @Override
    public ResourceLocation getId() { return ID; }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate predicate, DeserializationContext ctx) {
        String tier = json.has("tier") ? GsonHelper.getAsString(json, "tier") : null;
        return new TriggerInstance(predicate, tier);
    }

    /** @param newTierName the tier the mirror was upgraded TO (e.g. "stone", "netherite") */
    public void trigger(ServerPlayer player, String newTierName) {
        this.trigger(player, inst -> inst.matches(newTierName));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {

        @Nullable private final String requiredTier;

        public TriggerInstance(ContextAwarePredicate predicate, @Nullable String requiredTier) {
            super(ID, predicate);
            this.requiredTier = requiredTier;
        }

        /** Matches any tier upgrade. */
        public static TriggerInstance any() { return new TriggerInstance(ContextAwarePredicate.ANY, null); }

        /** Matches upgrade to a specific tier. */
        public static TriggerInstance ofTier(String tier) { return new TriggerInstance(ContextAwarePredicate.ANY, tier); }

        public boolean matches(String tier) {
            return requiredTier == null || requiredTier.equals(tier);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx) {
            JsonObject json = super.serializeToJson(ctx);
            if (requiredTier != null) json.addProperty("tier", requiredTier);
            return json;
        }
    }
}
