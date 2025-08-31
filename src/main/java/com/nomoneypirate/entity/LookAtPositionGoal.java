package com.nomoneypirate.entity;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;

public class LookAtPositionGoal extends Goal {

    private final MobEntity mob;
    private final double positionX;
    private final double positionY;
    private final double positionZ;

    public LookAtPositionGoal(MobEntity mob, double positionX, double positionY, double positionZ) {
        this.mob = mob;
        this.positionX = positionX;
        this.positionY = positionY;
        this.positionZ = positionZ;
    }

    @Override
    public boolean canStart() {
        double distance = mob.squaredDistanceTo(positionX, positionY, positionZ);
        return distance < 64.0; // 8 blocks
        //return true; // Always active
    }

    @Override
    public void tick() {
        mob.getLookControl().lookAt(positionX, positionY, positionZ);
    }

}