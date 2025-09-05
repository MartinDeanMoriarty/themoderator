package com.nomoneypirate.entity;

import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.LlmClient;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;

import static com.nomoneypirate.events.ModEvents.applyDecision;

public class FollowPlayerGoal extends Goal {
    private final MobEntity mob;
    private final PlayerEntity target;
    private final double speed;

    private Vec3d lastPosition = Vec3d.ZERO;
    private int stuckCounter = 0;

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
        if (target == null || !target.isAlive()) return;
        MinecraftServer server = target.getServer();

        double distanceSq = mob.squaredDistanceTo(target);
        double stopDistanceSq = 3.0 * 3.0;

        if (distanceSq > stopDistanceSq) {
            mob.getNavigation().startMovingTo(target, speed);

            // Stuck detection
            Vec3d currentPos = mob.getPos();
            if (currentPos.squaredDistanceTo(lastPosition) < 0.01) {
                stuckCounter++;
                if (stuckCounter > 40) {
                    String feedback = ConfigLoader.lang.feedback_28; // Avatar is stuck
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.feedback_49.formatted(feedback)).thenAccept(dec -> applyDecision(server, dec));
                }
            } else {
                stuckCounter = 0;
            }
            lastPosition = currentPos;
        } else {
            mob.getNavigation().stop(); // Mob bleibt stehen, wenn er nah genug ist
        }
    }
}