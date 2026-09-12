package io.quarkiverse.networkfaults.langchain4j.runtime.internal;

import java.util.Map;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.annotations.StaticInit;

@Recorder
public class AiModelTargetsRecorder {

    /**
     * The resolver bean, wrapped since a final class cannot be proxied, made with what the build found: the provider of each
     * model, the model of each AI service.
     */
    @StaticInit
    public RuntimeValue<AiModelTargets.Resolver> resolver(Map<String, Map<String, String>> modelProviders,
            Map<String, String> aiServiceChatModels) {
        return new RuntimeValue<>(new AiModelTargets.Resolver(modelProviders, aiServiceChatModels));
    }
}
