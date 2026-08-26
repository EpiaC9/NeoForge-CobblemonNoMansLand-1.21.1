package net.epiac9.cobblemonnml.events.quest;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = "cobblemonnml")
public final class QuestDataReloadListener {
    private QuestDataReloadListener() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) QuestDataManager::reload);
    }
}
