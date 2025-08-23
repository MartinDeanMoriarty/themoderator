package com.nomoneypirate;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

public class FollowPlayerGoal extends Goal {
    private final MobEntity mob;
    private final PlayerEntity target;
    private final double speed;

    public FollowPlayerGoal(MobEntity mob, PlayerEntity target, double speed) {
        this.mob = mob;
        this.target = target;
        this.speed = speed;
    }

    @Override
    public boolean canStart() {
        return target != null && target.isAlive();
    }

    @Override
    public void tick() {
        mob.getNavigation().startMovingTo(target, speed);
    }
}