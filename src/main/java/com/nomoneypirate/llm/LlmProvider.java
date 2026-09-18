package com.nomoneypirate.llm;

import java.util.concurrent.CompletableFuture;

public interface LlmProvider {
    CompletableFuture<LlmResult> moderateAsync(LlmClient.ModerationType type, String arg);
}