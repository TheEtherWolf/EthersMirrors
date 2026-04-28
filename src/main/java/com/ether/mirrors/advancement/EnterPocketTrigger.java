package com.ether.mirrors.advancement;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class EnterPocketTrigger extends SimpleCriterionTrigger<EnterPocketTrigger.TriggerInstance> {

    public static final ResourceLocation ID = new ResourceLocation("ethersmirrors", "enter_pocket");

    @Override
    public ResourceLocation getId() { return ID; }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate predicate, DeserializationContext ctx) {
        return new TriggerInstance(predicate);
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, inst -> true);
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {
        public TriggerInstance(ContextAwarePredicate predicate) { super(ID, predicate); }

        public static TriggerInstance instance() { return new TriggerInstance(ContextAwarePredicate.ANY); }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx) { return super.serializeToJson(ctx); }
    }
}
