package com.nomoneypirate.entity;

import com.nomoneypirate.actions.ModDecisions;
import com.nomoneypirate.config.ConfigLoader;
import com.nomoneypirate.llm.LlmClient;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

public class GotoPlayerGoal  extends Goal {
    private final MobEntity mob;
    private final PlayerEntity target;
    private final double speed;
    private int stuckCounter = 0;

    public GotoPlayerGoal(MobEntity mob, PlayerEntity target, double speed) {
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

            if (mob.getNavigation().isIdle()) {
                stuckCounter++;
                if (stuckCounter > 40) { // 2 seconds at 20 TPS
                    // Mob is stuck or target unreachable
                    String feedback = ConfigLoader.lang.feedback_28;
                    // Feedback to llm
                    LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> ModDecisions.applyDecision(server, dec));
                }
            } else {
                stuckCounter = 0; // Reset when movement is detected
            }
        } else {
            mob.getNavigation().stop();
            // Mob has reached target
            String feedback = ConfigLoader.lang.feedback_29.formatted(target);
            // Feedback to llm
            LlmClient.moderateAsync(LlmClient.ModerationType.FEEDBACK, ConfigLoader.lang.contextFeedback_03.formatted(feedback)).thenAccept(dec -> ModDecisions.applyDecision(server, dec));

        }
    }

}