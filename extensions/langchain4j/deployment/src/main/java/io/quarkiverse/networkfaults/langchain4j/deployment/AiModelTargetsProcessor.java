package io.quarkiverse.networkfaults.langchain4j.deployment;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Singleton;

import io.quarkiverse.langchain4j.deployment.DeclarativeAiServiceBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedAudioTranscriptionModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedImageModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedModerationModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedScoringModelProviderBuildItem;
import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultTargetBuildItem;
import io.quarkiverse.networkfaults.deployment.spi.NetworkFaultsEnabled;
import io.quarkiverse.networkfaults.langchain4j.runtime.internal.AiModelTargets;
import io.quarkiverse.networkfaults.langchain4j.runtime.internal.AiModelTargets.Kind;
import io.quarkiverse.networkfaults.langchain4j.runtime.internal.AiModelTargetsRecorder;
import io.quarkiverse.networkfaults.spi.BeanTargetResolver;
import io.quarkiverse.networkfaults.spi.TargetDescriptor;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

/**
 * One target per model Quarkus LangChain4j selected, and the resolver bean with the facts it needs, from the
 * extension's own build items: which provider serves each model name, and which chat model each declared AI service
 * uses.
 */
@BuildSteps(onlyIf = NetworkFaultsEnabled.class, onlyIfNot = IsNormal.class)
class AiModelTargetsProcessor {

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void targets(List<SelectedChatModelProviderBuildItem> chat,
            List<SelectedEmbeddingModelCandidateBuildItem> embedding,
            List<SelectedImageModelProviderBuildItem> image,
            List<SelectedModerationModelProviderBuildItem> moderation,
            List<SelectedScoringModelProviderBuildItem> scoring,
            List<SelectedAudioTranscriptionModelProviderBuildItem> audio,
            List<DeclarativeAiServiceBuildItem> aiServices,
            BuildProducer<NetworkFaultTargetBuildItem> targets,
            BuildProducer<SyntheticBeanBuildItem> beans,
            AiModelTargetsRecorder recorder) {
        Map<String, Map<String, String>> modelProviders = new HashMap<>();
        // the same endpoint under two kinds is one target
        Set<TargetDescriptor> descriptors = new LinkedHashSet<>();
        for (SelectedChatModelProviderBuildItem m : chat) {
            add(modelProviders, descriptors, Kind.CHAT, m.getProvider(), m.getConfigName());
        }
        for (SelectedEmbeddingModelCandidateBuildItem m : embedding) {
            add(modelProviders, descriptors, Kind.EMBEDDING, m.getProvider(), m.getConfigName());
        }
        for (SelectedImageModelProviderBuildItem m : image) {
            add(modelProviders, descriptors, Kind.IMAGE, m.getProvider(), m.getConfigName());
        }
        for (SelectedModerationModelProviderBuildItem m : moderation) {
            add(modelProviders, descriptors, Kind.MODERATION, m.getProvider(), m.getConfigName());
        }
        for (SelectedScoringModelProviderBuildItem m : scoring) {
            add(modelProviders, descriptors, Kind.SCORING, m.getProvider(), m.getConfigName());
        }
        for (SelectedAudioTranscriptionModelProviderBuildItem m : audio) {
            add(modelProviders, descriptors, Kind.AUDIO_TRANSCRIPTION, m.getProvider(), m.getConfigName());
        }
        for (TargetDescriptor descriptor : descriptors) {
            targets.produce(new NetworkFaultTargetBuildItem(descriptor));
        }
        Map<String, String> aiServiceChatModels = new HashMap<>();
        for (DeclarativeAiServiceBuildItem service : aiServices) {
            String modelName = service.getChatModelName();
            aiServiceChatModels.put(service.getServiceClassInfo().name().toString(),
                    modelName == null || modelName.isBlank() ? AiModelTargets.DEFAULT_MODEL : modelName);
        }
        beans.produce(SyntheticBeanBuildItem.configure(AiModelTargets.Resolver.class)
                .addType(BeanTargetResolver.class)
                .scope(Singleton.class)
                .runtimeValue(recorder.resolver(modelProviders, aiServiceChatModels))
                .done());
    }

    /**
     * A selected model: which provider serves it, and the target it becomes.
     */
    private static void add(Map<String, Map<String, String>> modelProviders, Set<TargetDescriptor> descriptors, Kind kind,
            String provider, String configName) {
        Map<String, String> byName = modelProviders.get(kind.name());
        if (byName == null) {
            byName = new HashMap<>();
            modelProviders.put(kind.name(), byName);
        }
        byName.put(configName, provider);
        descriptors.add(AiModelTargets.descriptor(provider, configName));
    }
}
