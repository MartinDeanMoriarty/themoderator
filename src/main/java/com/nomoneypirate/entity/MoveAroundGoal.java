package com.nomoneypirate.entity;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.Random;

public class MoveAroundGoal extends Goal {
    private final MobEntity mob;
    private final double blockRadius;
    private final double speed;
    private final World world;
    private final Random random = new Random();

    public MoveAroundGoal(MobEntity mob, double blockRadius, double speed) {
        this.mob = mob;
        this.blockRadius = blockRadius;
        this.speed = speed;
        this.world = mob.getWorld();
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        // Random activation
        return mob.getNavigation().isIdle() && random.nextInt(40) == 0;
    }

    @Override
    public void start() {
        Vec3d targetPos = getRandomPosition();
        if (targetPos != null) {
            mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, speed);
        }
    }

    private Vec3d getRandomPosition() {
        BlockPos mobPos = mob.getBlockPos();

        for (int i = 0; i < 10; i++) { // Try up to 10 different positions
            double dx = (random.nextDouble() * 2 - 1) * blockRadius;
            double dz = (random.nextDouble() * 2 - 1) * blockRadius;
            BlockPos target = mobPos.add((int) dx, 0, (int) dz);

            if (world.isAir(target) && mob.getNavigation().isValidPosition(BlockPos.ofFloored(Vec3d.ofCenter(target)))) {
                return Vec3d.ofCenter(target);
            }
        }

        return null; // No position found
    }
}
