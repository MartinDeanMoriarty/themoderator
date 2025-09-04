package com.nomoneypirate.entity;

import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.LlmClient;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import static com.nomoneypirate.events.ModEvents.applyDecision;

public class GotoPositionGoal  extends Goal {
    private final MobEntity mob;
    private final double posX;
    private final double posZ;
    private final double speed;
    private int stuckCounter = 0;

    public GotoPositionGoal(MobEntity mob, double posX, double posZ, double speed) {
        this.mob = mob;
        this.posX = posX;
        this.posZ = posZ;
        this.speed = speed;
    }

    @Override
    public boolean canStart() {
        return true;
    }

    @Override
    public void tick() {
        World world = mob.getWorld();
        BlockPos surfacePos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, new BlockPos((int) posX, 0, (int) posZ));
        double posY = surfacePos.getY();

        double distanceSq = mob.squaredDistanceTo(posX, posY, posZ);
        double stopDistanceSq = 3.0 * 3.0;

        if (distanceSq > stopDistanceSq) {
            mob.getNavigation().startMovingTo(posX, posY, posZ, speed);

            if (mob.getNavigation().isIdle()) {
                stuckCounter++;
                if (stuckCounter > 40) {
                    String feedback = ConfigLoader.lang.feedback_28;
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback, null).thenAccept(dec -> applyDecision(mob.getServer(), dec));
                }
            } else {
                stuckCounter = 0;
            }
        } else {
            mob.getNavigation().stop();
            String feedback = ConfigLoader.lang.feedback_31.formatted(posX, posZ);
            LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, feedback, null).thenAccept(dec -> applyDecision(mob.getServer(), dec));
        }
    }

}