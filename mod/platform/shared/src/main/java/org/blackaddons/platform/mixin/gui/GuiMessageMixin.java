package org.blackaddons.platform.mixin.gui;

import kotlin.text.Regex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.blackaddons.api.chat.ChatMessage;
import org.blackaddons.api.util.StringExtKt;
import org.blackaddons.platform.chat.ChatLines;
import org.blackaddons.platform.chat.StoredChatLine;
import org.blackaddons.platform.compat.McCommonKt;
import org.blackaddons.platform.impl.chat.ChatComponentsKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.awt.*;

@Mixin(GuiMessage.class)
public abstract class GuiMessageMixin implements StoredChatLine {

    @Shadow public abstract Component content();

    @Unique private String blackaddons$raw;
    @Unique private Color blackaddons$highlight;
    @Unique private Regex blackaddons$highlightMatches;
    @Unique private long blackaddons$addedAt;

    @Override
    public String getRaw() {
        return blackaddons$raw != null ? blackaddons$raw : content().getString();
    }

    @Override
    public String getUnformatted() {
        return StringExtKt.stripFormatting(getRaw());
    }

    @Override
    public ChatMessage getMessage() {
        return ChatComponentsKt.toChatMessage(content());
    }

    @Override
    public Color getHighlight() {
        return blackaddons$highlight;
    }

    @Override
    public void setHighlight(Color highlight) {
        blackaddons$highlight = highlight;
    }

    @Override
    public Regex getHighlightMatches() {
        return blackaddons$highlightMatches;
    }

    @Override
    public void setHighlightMatches(Regex highlightMatches) {
        blackaddons$highlightMatches = highlightMatches;
    }

    @Override
    public void remove() {
        ((ChatLines) McCommonKt.getChatComponent(Minecraft.getInstance()))
            .blackaddonsRemoveMessage((GuiMessage) (Object) this);
    }

    @Override
    public void blackaddonsSetRaw(String raw) {
        blackaddons$raw = raw;
    }

    @Override
    public long getBlackaddonsAddedAt() {
        return blackaddons$addedAt;
    }

    @Override
    public void setBlackaddonsAddedAt(long addedAt) {
        blackaddons$addedAt = addedAt;
    }

    @Override
    public void blackaddonsCopyStateTo(StoredChatLine other) {
        other.setHighlight(blackaddons$highlight);
        other.setHighlightMatches(blackaddons$highlightMatches);
        other.setBlackaddonsAddedAt(blackaddons$addedAt);
    }
}
