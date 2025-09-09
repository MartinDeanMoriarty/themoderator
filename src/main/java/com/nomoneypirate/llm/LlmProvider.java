package com.nomoneypirate.llm;

import java.util.concurrent.CompletableFuture;

public interface LlmProvider {
    CompletableFuture<ModerationDecision> moderateAsync(LlmClient.ModerationType type, String arg);
}