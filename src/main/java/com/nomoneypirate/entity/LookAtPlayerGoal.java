package com.nomoneypirate.entity;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

public class LookAtPlayerGoal extends Goal {

    private final MobEntity mob;
    private final PlayerEntity target;

    public LookAtPlayerGoal(MobEntity mob, PlayerEntity target) {
        this.mob = mob;
        this.target = target;
    }

    @Override
    public boolean canStart() {
        return this.mob.getTarget() instanceof PlayerEntity;
    }

    @Override
    public void tick() {
        if (target != null) {
            this.mob.getLookControl().lookAt(target, 30.0F, 30.0F);
        }
    }

}